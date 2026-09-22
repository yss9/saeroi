package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.upcycle.agent.entity.AgentMessage;
import com.ssafy.upcycle.agent.entity.AgentMessageRole;
import com.ssafy.upcycle.agent.repository.AgentConversationRepository;
import com.ssafy.upcycle.agent.repository.AgentMessageRepository;
import com.ssafy.upcycle.agent.repository.AgentPendingActionRepository;
import com.ssafy.upcycle.agent.service.AgentConversationService;
import com.ssafy.upcycle.agent.service.llm.LlmClient;
import com.ssafy.upcycle.agent.service.llm.LlmCompletion;
import com.ssafy.upcycle.agent.service.llm.LlmMessage;
import com.ssafy.upcycle.agent.service.llm.LlmRole;
import com.ssafy.upcycle.production.dto.ProductionBatchResponse;
import com.ssafy.upcycle.production.dto.ProductionInputResponse;
import com.ssafy.upcycle.production.entity.ProductionBatchStatus;
import com.ssafy.upcycle.production.service.ProductionBatchService;
import com.ssafy.upcycle.trade.dto.PageResponse;
import com.ssafy.upcycle.trade.dto.TradeResponse;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import com.ssafy.upcycle.trade.service.TradeQueryService;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 지금 상태를 모델에게 넣는가, 그리고 <b>담당자 눈에는 안 보이는가.</b>
 *
 * <p>모델이 이력에 남은 낡은 조회 결과를 믿고 사실과 다르게 설명한 일이 있었다. 결제 전에
 * 받은 {@code needsChainOrder:false} 를 근거로 "온체인 기록이 남지 않는 거래" 라고 말했는데,
 * 판매 글은 온체인에 등록돼 있었고 거래는 서명만 기다리는 상태였다. 프롬프트에 "결제 후
 * 곧바로 조회하라" 고 적어도 경량 모델은 지키지 않아서, 서버가 넣어 준다.
 *
 * <p><b>그 자리가 시스템 프롬프트여야 한다.</b> 처음에는 사건 보고 문장에 이어 붙여 이력에
 * ASSISTANT 로 넣었는데, 세 가지가 동시에 망가졌다.
 *
 * <ol>
 *   <li>담당자가 모델용 지시문을 읽었다 — "이 목록이 정본이다", "request_chain_order 로
 *       서명 카드를 띄운다" 가 대화창에 그대로 떴다.
 *   <li>모델이 그것을 <b>자기 말투로 배웠다.</b> ASSISTANT 역할이라 자기가 하는 말로 보였고,
 *       다음 턴에 그 블록을 통째로 베껴 거래 번호만 바꿔 출력했다. 아직 결제도 안 된 거래에
 *       "결제는 끝났고 온체인 기록이 아직이다" 라는 없는 사실까지 지어냈다.
 *   <li><b>특정 순간에만 들어갔다.</b> 결제 복귀 같은 사건 때만이어서, 그 순간을 놓친 단계는
 *       그 뒤로 모델의 시야에서 사라졌다. 결제만 되고 온체인 주문이 없는 거래가 그렇게 잊혔다.
 * </ol>
 */
@SpringBootTest
class AgentEventStateTests {

    @Autowired private AgentConversationService conversationService;
    @Autowired private AgentConversationRepository conversations;
    @Autowired private AgentMessageRepository messages;
    @Autowired private AgentPendingActionRepository pendingActions;
    @Autowired private UserRepository users;

    @MockitoBean private LlmClient llmClient;
    @MockitoBean private TradeQueryService tradeQuery;
    @MockitoBean private ProductionBatchService batchService;

    /** 대화보다 먼저·나중에 열린 배치를 가르는 시각. 지난 작업의 흔적인지 판정한다. */
    private static final LocalDateTime EARLIER = LocalDateTime.of(2020, 1, 1, 0, 0);
    private static final LocalDateTime LATER = LocalDateTime.of(2999, 1, 1, 0, 0);

    private Long brandId;

    @BeforeEach
    void setUp() {
        brandId = users.save(User.register(
                        "agent-event@test.com", "{noop}x", "brand", "010-0000-0000", UserRole.BRAND))
                .getId();
    }

    @AfterEach
    void clean() {
        pendingActions.deleteAll();
        messages.deleteAll();
        conversations.deleteAll();
        users.deleteAll();
    }

    /** 막힌 단계는 <b>담당자가 말을 걸 때마다</b> 모델 눈앞에 있어야 한다. 사건 때만이 아니다. */
    @Test
    void 막힌_단계는_매_턴_시스템_프롬프트로_들어간다() {
        givenTrades(trade(16L, TradeStatus.PAID, null));
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));

        // 사건 보고가 아니라 평범한 발화다.
        var detail = conversationService.start(brandId, "소재 사고 싶어요");
        conversationService.send(brandId, detail.id(), "지금 어디까지 됐어?");

        String system = lastSystemPrompts();
        assertThat(system).contains("request_chain_order");
        assertThat(system).contains("이 목록이 정본이다");
    }

    /** 담당자에게는 사실만 남는다. 지시문이 말풍선으로 뜨면 대화가 아니라 로그가 된다. */
    @Test
    void 사건_보고에는_모델용_지시문이_섞이지_않는다() {
        givenTrades(trade(16L, TradeStatus.PAID, null));

        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));
        var detail = conversationService.start(brandId, "소재 사고 싶어요");

        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("온체인 기록을 남기겠습니다.", List.of()));
        conversationService.reportEvent(brandId, detail.id(), "결제가 완료되었습니다. (거래 16번)");

        assertThat(eventNote()).isEqualTo("결제가 완료되었습니다. (거래 16번)");
        // 화면에 그려지는 어떤 줄에도 도구 이름과 지시문이 없어야 한다.
        assertThat(visibleMessages())
                .noneMatch(content -> content.contains("request_chain_order")
                        || content.contains("이 목록이 정본이다"));
    }

    /**
     * 열어 둔 생산 배치의 <b>번호</b>를 알려준다.
     *
     * <p>카드가 승인되면 실행 결과는 액션 행에만 저장되고 이력에는 사람이 읽을 요약만 들어간다.
     * 그래서 모델은 방금 열린 배치의 번호를 본 적이 없고, 유일하게 본 숫자인 카드 번호를
     * 도메인 번호로 썼다 — 카드 44번을 승인해 배치 3번이 열렸는데 {@code batchId:44} 를
     * 넘겨 "생산 배치를 찾을 수 없습니다" 로 끝났다.
     */
    @Test
    void 열린_생산_배치의_번호를_알려준다() {
        givenTrades();
        givenDraftBatch(3L, "열쇠고리 1차 생산", 10, 0L);
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));

        var detail = conversationService.start(brandId, "생산 진행할게");
        conversationService.send(brandId, detail.id(), "소재 투입해줘");

        String system = lastSystemPrompts();
        assertThat(system).contains("생산 배치 3(열쇠고리 1차 생산, 10개 예정)");
        assertThat(system).contains("add_production_inputs");
    }

    /** 투입이 끝난 배치는 다음 일이 확정이다. 같은 줄에 투입을 또 권하면 모델이 되풀이한다. */
    @Test
    void 투입이_끝난_배치는_확정을_안내한다() {
        givenTrades();
        givenDraftBatch(3L, "열쇠고리 1차 생산", 10, 2000L);
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));

        var detail = conversationService.start(brandId, "생산 진행할게");
        conversationService.send(brandId, detail.id(), "다음은?");

        String system = lastSystemPrompts();
        assertThat(system).contains("confirm_production");
        // 잠긴 양을 숫자로 알려야 담당자가 무엇이 묶였는지 안다.
        assertThat(system).contains("2,000g");
        assertThat(system).contains("cancel_production_batch");
        // 소재가 들어 있는 배치를 "빈 배치" 로 분류하면 묶인 소재가 대화에서 사라진다.
        assertThat(system).doesNotContain("지난 작업의 흔적인 빈 배치");
    }

    /**
     * 지난 테스트에서 열어 두고 만 빈 배치는 <b>할 일이 아니다.</b>
     *
     * <p>폐기할 수단이 없어 영원히 {@code DRAFT} 로 남는다. 모두 할 일로 올리자 모델이 매번
     * "배치 3과 4의 소재 투입과 생산 확정이 남았습니다. 이어서 진행할까요?" 라고 졸랐다.
     *
     * <p>"가장 최근 배치 하나만" 으로 좁힌 시도도 실패했다 — 번호 순서는 진행 여부를 말해
     * 주지 않아서, 방치된 둘 중 번호가 큰 쪽이 할 일로 뽑혔다. 기준은 시간이어야 한다.
     */
    @Test
    void 대화보다_먼저_열린_빈_배치는_할_일이_아니다() {
        givenTrades();
        ProductionBatchResponse stray = draft(3L, "열쇠고리 1차 생산", 10, 0L, EARLIER);
        ProductionBatchResponse otherStray = draft(4L, "2026 가죽 토드백", 1, 0L, EARLIER);
        givenBatches(stray, otherStray);
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));

        var detail = conversationService.start(brandId, "생산 진행할게");
        conversationService.send(brandId, detail.id(), "진행단계 확인해줘");

        String system = lastSystemPrompts();
        // 번호는 알려 둔다 — 담당자가 그 배치를 말할 때 지어내지 않아야 한다.
        assertThat(system).contains("지난 작업의 흔적인 빈 배치").contains("3(").contains("4(");
        assertThat(system).contains("할 일이 아니다");
        // 그러나 투입을 권하는 문장은 없어야 한다.
        assertThat(system).doesNotContain("add_production_inputs 에 이 배치 번호를 넘긴다");
    }

    /** 소재가 투입된 배치는 시점과 무관하게 할 일이다. 확정하지 않으면 그 소재가 묶인 채 남는다. */
    @Test
    void 소재가_투입된_옛_배치는_여전히_할_일이다() {
        givenTrades();
        givenBatches(draft(3L, "열쇠고리 1차 생산", 10, 2000L, EARLIER));
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));

        var detail = conversationService.start(brandId, "생산 진행할게");
        conversationService.send(brandId, detail.id(), "진행단계 확인해줘");

        assertThat(lastSystemPrompts()).contains("confirm_production");
    }

    @Test
    void 할_일이_없으면_시스템_블록을_붙이지_않는다() {
        // 완료된 거래만 있으면 손이 필요한 일이 없다. 빈 목록을 붙이면 입력 토큰만 늘고,
        // "없음" 을 읽은 모델이 할 일을 지어낼 여지가 생긴다.
        givenTrades(trade(15L, TradeStatus.COMPLETED, 26L));
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));

        var detail = conversationService.start(brandId, "안녕");
        conversationService.send(brandId, detail.id(), "상태 알려줘");

        assertThat(systemBlockCount()).isEqualTo(1);
        assertThat(lastSystemPrompts()).doesNotContain("지금 담당자 손이 필요한 일");
    }

    private TradeResponse trade(Long id, TradeStatus status, Long chainTradeId) {
        TradeResponse trade = mock(TradeResponse.class);
        when(trade.tradeId()).thenReturn(id);
        when(trade.status()).thenReturn(status);
        when(trade.chainTradeId()).thenReturn(chainTradeId);
        when(trade.materialName()).thenReturn("리얼 소가죽");
        return trade;
    }

    private void givenDraftBatch(Long batchId, String name, int producedQuantity, long totalUsed) {
        // 이 대화가 시작된 뒤에 열린 배치. 지금 진행 중인 것이다.
        givenBatches(draft(batchId, name, producedQuantity, totalUsed, LATER));
    }

    /**
     * <b>목을 먼저 다 만든 뒤에 스텁한다.</b> {@code when(...)} 의 인자 안에서 다른
     * {@code when(...)} 을 부르면 Mockito 가 {@code UnfinishedStubbing} 으로 거부한다 —
     * 바깥 스텁이 아직 끝나지 않은 상태에서 안쪽 스텁이 시작되기 때문이다.
     */
    private void givenBatches(ProductionBatchResponse... batches) {
        List<ProductionBatchResponse> prepared = List.of(batches);
        when(batchService.findMyBatches(any())).thenReturn(prepared);
    }

    /**
     * {@code DRAFT} 배치 스텁.
     *
     * <p><b>잠긴 양은 투입 목록으로 표현한다.</b> {@code totalUsedQuantity} 는 확정 시점에야
     * 채워지고 그 전까지 언제나 0 이므로, 그것으로 "소재가 들어 있는 배치" 를 흉내내면 현실에
     * 없는 상태를 검증하게 된다. 실제로 그 값으로 판정하다가 소재가 잠긴 배치를 "빈 배치" 로
     * 분류하는 버그를 만들었고, 이 스텁이 그것을 통과시켰다.
     */
    private ProductionBatchResponse draft(
            Long batchId, String name, int producedQuantity, long lockedInput, LocalDateTime createdAt) {
        ProductionBatchResponse batch = mock(ProductionBatchResponse.class);
        when(batch.productionBatchId()).thenReturn(batchId);
        when(batch.batchName()).thenReturn(name);
        when(batch.producedQuantity()).thenReturn(producedQuantity);
        when(batch.status()).thenReturn(ProductionBatchStatus.DRAFT);
        // 확정 전이므로 0 이다 — 현실과 같게 둔다.
        when(batch.totalUsedQuantity()).thenReturn(0L);
        when(batch.createdAt()).thenReturn(createdAt);
        when(batch.inputs()).thenReturn(lockedInput == 0
                ? List.of()
                : List.of(new ProductionInputResponse(
                        1L, 33L, "MAT-1", "실크 원단", lockedInput, lockedInput, 0L, 0L, null, 1L, null)));
        return batch;
    }

    private void givenTrades(TradeResponse... trades) {
        when(tradeQuery.findMyTrades(any(), any()))
                .thenReturn(new PageResponse<>(List.of(trades), 0, 10, trades.length, 1, false));
    }

    /** 사건 보고로 들어간 줄. 모델의 답변이 아니라 서버가 넣은 것이다. */
    private String eventNote() {
        return messages.findAll().stream()
                .filter(message -> message.getRole() == AgentMessageRole.ASSISTANT)
                .map(AgentMessage::getContent)
                .filter(content -> content != null && content.startsWith("결제가 완료되었습니다"))
                .findFirst()
                .orElseThrow();
    }

    /** 화면이 그리는 줄. tool 호출·결과는 걸러진다 ({@code ConversationDetailResponse}). */
    private List<String> visibleMessages() {
        return messages.findAll().stream()
                .filter(message -> message.getRole() == AgentMessageRole.USER
                        || message.getRole() == AgentMessageRole.ASSISTANT)
                .map(AgentMessage::getContent)
                .filter(content -> content != null)
                .toList();
    }

    /** 마지막 호출에 실린 시스템 블록들을 이어 붙인 것. */
    private String lastSystemPrompts() {
        return String.join(System.lineSeparator(), systemBlocks());
    }

    private int systemBlockCount() {
        return systemBlocks().size();
    }

    @SuppressWarnings("unchecked")
    private List<String> systemBlocks() {
        ArgumentCaptor<List<LlmMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmClient, atLeastOnce()).complete(captor.capture(), anyList());
        return captor.getAllValues().getLast().stream()
                .filter(message -> message.role() == LlmRole.SYSTEM)
                .map(LlmMessage::content)
                .toList();
    }
}
