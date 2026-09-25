package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.service.AgentConversationService;
import com.ssafy.upcycle.agent.service.llm.LlmClient;
import com.ssafy.upcycle.blockchain.service.ChainProductionStatusReader;
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
import java.util.Optional;
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
    @MockitoBean private ChainProductionStatusReader chainBatches;

    /**
     * 배치·거래가 만들어진 시각. <b>이제 판정에 쓰이지 않는다</b> — 이 대화의 일인지는 승인된
     * 카드가 정한다({@code AgentProjects}). 시각이 달라도 결과가 같다는 것을 보이려고 남긴다.
     */
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
        ProjectCards.boughtTrade(pendingActions, detail.id(), 16L);
        conversationService.send(brandId, detail.id(), "지금 어디까지 됐어?");

        // 도구 이름만 보면 안 된다 — 기본 프롬프트에도 "이런 지시를 옮겨 쓰지 말라" 는 예로
        // 같은 문자열이 들어 있다. 이 거래의 줄이 실제로 있는지 본다.
        String system = lastSystemPrompts();
        assertThat(system).contains("거래 16(리얼 소가죽 5kg)");
        assertThat(system).contains("request_chain_order 로 서명 카드를 띄운다");
    }

    /**
     * 사건 보고는 <b>모델이 한 번 전한다.</b> 서버 문장을 먼저 띄우면 모델이 곧이어 같은 말을
     * 한 번 더 한다 — "결제가 완료되었습니다" 다음에 "결제가 완료됐네요" 가 붙었다.
     *
     * <p>담당자에게는 사실만 보인다. 지시문이 말풍선으로 뜨면 대화가 아니라 로그가 된다.
     */
    @Test
    void 사건_보고는_모델에게_가고_담당자에게는_모델_문장만_보인다() {
        givenTrades(trade(16L, TradeStatus.PAID, null));

        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));
        var detail = conversationService.start(brandId, "소재 사고 싶어요");

        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("결제가 끝났습니다. 이제 온체인 기록을 남기겠습니다.", List.of()));
        conversationService.reportEvent(brandId, detail.id(), "결제가 완료되었습니다. (거래 16번)");

        assertThat(lastSystemPrompts()).contains("결제가 완료되었습니다. (거래 16번)");
        assertThat(visibleMessages())
                .contains("결제가 끝났습니다. 이제 온체인 기록을 남기겠습니다.")
                .doesNotContain("결제가 완료되었습니다. (거래 16번)");
        // 화면에 그려지는 어떤 줄에도 도구 이름과 지시문이 없어야 한다.
        assertThat(visibleMessages())
                .noneMatch(content -> content.contains("request_chain_order")
                        || content.contains("이 목록이 정본이다"));
    }

    /**
     * 모델이 말하지 못해도 <b>결제 복귀는 실패가 아니다.</b> 오류를 올리면 복귀 화면이 "결제
     * 승인에 실패했습니다" 를 띄운다 — 결제는 이미 끝났는데.
     */
    @Test
    void 사건_보고_뒤_모델이_응답하지_못하면_사실이_한_번_남는다() {
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));
        var detail = conversationService.start(brandId, "소재 사고 싶어요");

        when(llmClient.complete(anyList(), anyList())).thenThrow(new IllegalStateException("llm down"));

        assertThatCode(() -> conversationService.reportEvent(
                        brandId, detail.id(), "결제가 완료되었습니다. (거래 16번)"))
                .doesNotThrowAnyException();
        assertThat(visibleMessages())
                .filteredOn("결제가 완료되었습니다. (거래 16번)"::equals)
                .hasSize(1);
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
        ProjectCards.openedBatch(pendingActions, detail.id(), 3L);
        conversationService.send(brandId, detail.id(), "소재 투입해줘");

        // 상태 블록에서 본다. 도구 이름은 늘 실리는 단계별 안내에도 나온다.
        String system = liveState();
        assertThat(system).contains("생산 배치 3(열쇠고리 1차 생산)");
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
        ProjectCards.openedBatch(pendingActions, detail.id(), 3L);
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
     * <b>이 대화가 열지 않은 배치는 할 일이 아니다.</b> 지난 대화나 홈페이지에서 연 배치다.
     *
     * <p>예전에는 서버가 어느 배치가 이 대화의 것인지 몰라 "대화보다 먼저 열린 빈 배치" 를
     * 시각으로 걸렀다. 그 전에는 모두 할 일로 올려, 모델이 매번 "배치 3과 4의 소재 투입과 생산
     * 확정이 남았습니다" 라고 졸랐다. 이제 이 대화에서 연 배치만 본다.
     */
    @Test
    void 이_대화가_열지_않은_배치는_할_일이_아니다() {
        givenTrades();
        givenBatches(
                draft(3L, "열쇠고리 1차 생산", 10, 0L, EARLIER),
                draft(4L, "2026 가죽 토드백", 1, 2000L, LATER),
                draft(5L, "에코백 1차 생산", 20, 0L, EARLIER));
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));

        var detail = conversationService.start(brandId, "생산 진행할게");
        ProjectCards.openedBatch(pendingActions, detail.id(), 5L);
        conversationService.send(brandId, detail.id(), "진행단계 확인해줘");

        String system = lastSystemPrompts();
        assertThat(system).contains("생산 배치 5(에코백 1차 생산)");
        // **소재가 들어 있어도, 나중에 열었어도** 이 대화의 배치가 아니면 먼저 꺼내지 않는다.
        // 담당자가 그 배치를 물으면 모델이 get_my_production_batches 로 조회해 처리한다.
        assertThat(system).doesNotContain("열쇠고리 1차 생산");
        assertThat(system).doesNotContain("2026 가죽 토드백");
    }

    /** 이 대화에서 연 배치는 언제 열었든 할 일이다. 확정하지 않으면 그 소재가 묶인 채 남는다. */
    @Test
    void 이_대화의_배치는_열린_시각과_상관없이_할_일이다() {
        givenTrades();
        givenBatches(draft(3L, "열쇠고리 1차 생산", 10, 2000L, EARLIER));
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));

        var detail = conversationService.start(brandId, "생산 진행할게");
        ProjectCards.openedBatch(pendingActions, detail.id(), 3L);
        conversationService.send(brandId, detail.id(), "진행단계 확인해줘");

        assertThat(liveState()).contains("생산 배치 3(열쇠고리 1차 생산)").contains("confirm_production");
    }

    /**
     * 확정은 여정의 끝이 아니다. <b>체인 기록이 남고, 그다음 제품 연결이 남는다.</b>
     *
     * <p>목록에서 빠져 있어 실제로 멈춘 적이 있다 — 확정 뒤 이어지는 턴이 빈 응답으로 죽어 모델이
     * record_production_chain 을 꺼내지 못했고, 담당자는 그 단계가 있는 줄도 몰라 물어볼 수도 없었다.
     *
     * <p><b>기록이 남은 동안에는 기록 하나만 적는다.</b> 둘을 한꺼번에 적었더니 모델이 제품 등록을
     * 먼저 골라 판매가를 묻지 않고 0원으로 등록했고, 0원에 판매가 시작됐다.
     */
    @Test
    void 확정했지만_체인_기록이_남은_배치는_기록부터_알린다() {
        givenTrades();
        givenBatches(confirmed(6L, "실크 자투리 열쇠고리 끈 1차", 10));
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));

        var detail = conversationService.start(brandId, "생산 진행할게");
        ProjectCards.openedBatch(pendingActions, detail.id(), 6L);
        conversationService.send(brandId, detail.id(), "지금 뭐 해야 해?");

        String state = liveState();
        assertThat(state).contains("record_production_chain");
        assertThat(state).contains("온체인 기록이 아직이다");
        assertThat(state).contains("서명 4번");
        assertThat(state)
                .as("제품 정보는 기록 네 번을 마친 뒤에 정한다")
                .doesNotContain("판매할 제품 정보를 정할 차례입니다")
                .doesNotContain("→ register_product_model");
    }

    /**
     * <b>배치 번호가 있다고 기록이 끝난 것이 아니다.</b>
     *
     * <p>{@code chainBatchId} 는 네 번의 서명 중 첫 번째({@code createBatch})의 산물이다.
     * 담당자가 거기서 지갑을 닫으면 번호만 남는다. 그 값이 채워졌는지로 판정하던 동안
     * 배치 6은 확정된 채로 묻혀 있었다 — 체인에서는 {@code DRAFT} 인데 목록은 아무 말도
     * 하지 않았고, 확정이 소재를 DB 에서만 줄여 체인에서는 같은 소재가 그대로 남았다.
     */
    @Test
    void 첫_서명만_하고_멈춘_배치는_여전히_체인_기록이_남았다() {
        givenTrades();
        ProductionBatchResponse stalled = confirmed(6L, "실크 자투리 열쇠고리 끈 1차", 10);
        when(stalled.chainBatchId()).thenReturn(12L);
        givenBatches(stalled);
        // 체인은 1단계까지만 알고 있다.
        when(chainBatches.isConfirmedOnChain(12L)).thenReturn(Optional.of(false));
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));

        var detail = conversationService.start(brandId, "생산 진행할게");
        ProjectCards.openedBatch(pendingActions, detail.id(), 6L);
        conversationService.send(brandId, detail.id(), "지금 뭐 해야 해?");

        // 상태 블록에서 본다. 도구 이름은 늘 실리는 단계별 안내에도 나온다.
        assertThat(liveState()).contains("record_production_chain");
    }

    /**
     * 체인을 읽을 수 없으면 권하지 않는다.
     *
     * <p>체인을 쓰지 않는 환경에서 끝낼 방법이 없는 일을 목록에 남기면, 담당자는 지울 수
     * 없는 숙제를 보게 되고 모델은 매 턴 그것을 꺼낸다.
     */
    @Test
    void 체인을_읽지_못하면_기록을_권하지_않는다() {
        givenTrades();
        ProductionBatchResponse stalled = confirmed(6L, "실크 자투리 열쇠고리 끈 1차", 10);
        when(stalled.chainBatchId()).thenReturn(12L);
        when(stalled.productModelId()).thenReturn(3L);
        givenBatches(stalled);
        when(chainBatches.isConfirmedOnChain(12L)).thenReturn(Optional.empty());
        when(chainBatches.linkedItemCount(12L)).thenReturn(Optional.empty());
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));

        var detail = conversationService.start(brandId, "생산 진행할게");
        ProjectCards.openedBatch(pendingActions, detail.id(), 6L);
        conversationService.send(brandId, detail.id(), "지금 뭐 해야 해?");

        // 상태 블록만 본다. 도구 이름은 늘 실리는 단계별 안내에도 나온다(AgentPromptBuilder).
        assertThat(liveState()).doesNotContain("record_production_chain");
    }

    /**
     * <b>진행 막대와 대화가 같은 말을 해야 한다.</b>
     *
     * <p>둘이 각자 판정하던 동안, 배치 6에서 막대는 "블록체인 기록" 칸을 완료로 칠하고
     * 같은 화면의 대화는 "서명이 남았습니다" 라고 했다. 담당자는 어느 쪽이 맞는지 알 방법이
     * 없다. 막대가 {@code chainBatchId} 로만 칠했기 때문인데, 그 값은 네 번의 서명 중
     * 첫 번째의 산물이다.
     */
    @Test
    void 막대와_대화가_체인_기록에_대해_같은_말을_한다() {
        givenTrades();
        ProductionBatchResponse stalled = confirmed(6L, "실크 자투리 열쇠고리 끈 1차", 10);
        when(stalled.chainBatchId()).thenReturn(12L);
        when(stalled.upcycleRatio()).thenReturn(java.math.BigDecimal.valueOf(100));
        givenBatches(stalled);
        when(chainBatches.isConfirmedOnChain(12L)).thenReturn(Optional.of(false));
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));

        var detail = conversationService.start(brandId, "생산 진행할게");
        ProjectCards.openedBatch(pendingActions, detail.id(), 6L);
        var conversation = conversations.findById(detail.id()).orElseThrow();
        conversation.moveTo(JourneyPhase.PRODUCTION);
        conversations.save(conversation);

        var progress = conversationService.progress(brandId, detail.id());

        assertThat(progress.steps())
                .filteredOn(step -> step.label().equals("블록체인 기록"))
                .allMatch(step -> !step.done());
        assertThat(progress.nextTodo()).contains("블록체인 기록이 남았습니다");
    }

    /**
     * <b>새 대화에서 지난 여정의 거래를 보여주지 않는다.</b>
     *
     * <p>막대가 계정 전체에서 가장 최근 거래를 집던 동안, 대화를 새로 시작하자마자 지난
     * 거래가 떴다 — 아직 아무것도 사지 않았는데 "울 원단 1kg · 10,000원, 수령이 끝났습니다"
     * 가 보였다. 담당자는 지금 무엇을 하는 중인지 알 수 없다. 이제 막대는 이 대화에서 산
     * 거래만 그린다.
     */
    @Test
    void 새_대화에서는_끝난_지난_거래를_막대에_올리지_않는다() {
        TradeResponse old = trade(27L, TradeStatus.COMPLETED, 48L);
        when(old.createdAt()).thenReturn(EARLIER);
        when(old.receivedAt()).thenReturn(EARLIER);
        givenTrades(old);
        givenBatches();
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));

        var detail = conversationService.start(brandId, "소재 사고 싶어요");
        var conversation = conversations.findById(detail.id()).orElseThrow();
        conversation.moveTo(JourneyPhase.MATERIAL_SOURCING);
        conversations.save(conversation);

        var progress = conversationService.progress(brandId, detail.id());

        assertThat(progress.subject())
                .as("지난 거래를 현재 진행으로 그리면 방금 한 일로 읽힌다")
                .isNull();
        // 빈 막대로 두지 않는다 — 이 단계에서 거래가 없다는 것은 "소재를 고를 차례" 다.
        assertThat(progress.nextTodo()).contains("어떤 소재가 얼마나 필요한지");
    }

    /**
     * 지난 대화에서 산 소재가 아직 오는 중이어도 <b>이 대화의 막대에는 올리지 않는다.</b>
     *
     * <p>그 거래는 지난 대화의 제품 것이다. 예전에는 "끝나지 않았으면 여전히 담당자의 일" 이라며
     * 새 대화의 막대에 올렸는데, 그러면 새 제품을 시작한 담당자가 첫 화면에서 다른 제품의 배송을
     * 보게 된다. 그 거래는 지난 대화와 홈페이지 거래 목록에 남아 있고, 물으면 모델이 처리한다.
     */
    @Test
    void 지난_대화의_거래는_오는_중이어도_이_대화의_막대에_올리지_않는다() {
        givenTrades(shippingTrade(28L));
        givenBatches();
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));

        var detail = conversationService.start(brandId, "소재 사고 싶어요");
        moveTo(detail.id(), JourneyPhase.MATERIAL_SOURCING);

        var progress = conversationService.progress(brandId, detail.id());
        assertThat(progress.subject()).isNull();
        assertThat(progress.nextTodo()).contains("어떤 소재가 얼마나 필요한지");
    }

    /** 이 대화에서 산 소재가 오는 중이면 막대가 그것을 가리킨다. */
    @Test
    void 이_대화에서_산_소재가_오는_중이면_막대가_가리킨다() {
        givenTrades(shippingTrade(28L));
        givenBatches();
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));

        var detail = conversationService.start(brandId, "소재 사고 싶어요");
        ProjectCards.boughtTrade(pendingActions, detail.id(), 28L);
        moveTo(detail.id(), JourneyPhase.MATERIAL_SOURCING);

        // 막대는 모델에게 가는 목록과 같은 문장을 쓴다(아래 테스트 참고).
        assertThat(conversationService.progress(brandId, detail.id()).nextTodo())
                .isEqualTo("수령을 확정할 차례입니다");
    }

    /**
     * <b>막대 · 오른쪽 패널 · 모델이 같은 판정을 쓴다.</b>
     *
     * <p>셋이 각자 판정하던 동안 한 화면에서 서로 다른 말을 했다 — 막대는 "기록 완료", 대화는
     * "서명이 남았습니다". 오른쪽 패널은 거래만 볼 줄 알아 가공·생산의 할 일은 아예 없었다.
     */
    @Test
    void 막대와_패널과_모델이_같은_다음_할_일을_말한다() {
        givenTrades(trade(16L, TradeStatus.PAID, null));
        givenBatches();
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));

        var detail = conversationService.start(brandId, "소재 사고 싶어요");
        ProjectCards.boughtTrade(pendingActions, detail.id(), 16L);
        moveTo(detail.id(), JourneyPhase.MATERIAL_SOURCING);
        conversationService.send(brandId, detail.id(), "다음은?");

        var progress = conversationService.progress(brandId, detail.id());
        assertThat(progress.nextTodo()).isEqualTo("온체인 기록 서명이 남았습니다");
        assertThat(progress.tasks())
                .singleElement()
                .satisfies(task -> {
                    assertThat(task.domain()).isEqualTo("TRADE");
                    assertThat(task.referenceId()).isEqualTo(16L);
                    assertThat(task.label()).isEqualTo("온체인 기록 서명이 남았습니다");
                    assertThat(task.actionable()).isTrue();
                });
        // 모델에게는 다음 한 가지를 서버가 골라 준다. 목록에서 고르게 두면 승인 뒤 "완료되었습니다"
        // 로 끝내며 다음을 꺼내지 않았다.
        assertThat(lastSystemPrompts())
                .contains("**바로 다음 할 일:** 거래 16(리얼 소가죽 5kg) — 온체인 기록 서명이 남았습니다"
                        + " → request_chain_order");
    }

    /** 체인 기록과 제품 연결이 다 끝난 배치는 할 일이 아니다. */
    @Test
    void 끝난_배치는_알리지_않는다() {
        givenTrades();
        ProductionBatchResponse done = confirmed(6L, "끝난 배치", 10);
        when(done.chainBatchId()).thenReturn(12L);
        when(done.productModelId()).thenReturn(3L);
        givenBatches(done);
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));

        var detail = conversationService.start(brandId, "생산 진행할게");
        ProjectCards.openedBatch(pendingActions, detail.id(), 6L);
        conversationService.send(brandId, detail.id(), "지금 뭐 해야 해?");

        // 상태 블록만 본다. 도구 이름은 늘 실리는 단계별 안내에도 나온다(AgentPromptBuilder).
        assertThat(liveState()).doesNotContain("record_production_chain");
    }

    @Test
    void 할_일이_없으면_시스템_블록을_붙이지_않는다() {
        // 이 대화의 거래가 다 끝났으면 손이 필요한 일이 없다. 빈 목록을 붙이면 입력 토큰만
        // 늘고, "없음" 을 읽은 모델이 할 일을 지어낼 여지가 생긴다.
        givenTrades(trade(15L, TradeStatus.COMPLETED, 26L));
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));

        var detail = conversationService.start(brandId, "안녕");
        ProjectCards.boughtTrade(pendingActions, detail.id(), 15L);
        conversationService.send(brandId, detail.id(), "상태 알려줘");

        assertThat(systemBlockCount()).isEqualTo(1);
        assertThat(lastSystemPrompts()).doesNotContain("담당자 손이 필요한 일");
    }

    /**
     * 새 대화에서는 <b>아직 시작한 일이 없다</b>고 알린다. 홈페이지에서 산 소재의 할 일은 먼저
     * 꺼내지 않는다 — 이 대화의 제품 얘기인지 담당자가 알 수 없다.
     */
    @Test
    void 새_대화에서는_홈페이지_거래를_먼저_꺼내지_않는다() {
        givenTrades(trade(16L, TradeStatus.PAID, null));
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));

        var detail = conversationService.start(brandId, "에코백 만들고 싶어요");
        conversationService.send(brandId, detail.id(), "뭐부터 하면 돼?");

        String system = lastSystemPrompts();
        assertThat(system).contains("아직 이 대화에서 시작한 일이 없다");
        assertThat(system).doesNotContain("거래 16(");
    }

    private TradeResponse trade(Long id, TradeStatus status, Long chainTradeId) {
        TradeResponse trade = mock(TradeResponse.class);
        when(trade.tradeId()).thenReturn(id);
        when(trade.status()).thenReturn(status);
        when(trade.chainTradeId()).thenReturn(chainTradeId);
        when(trade.materialName()).thenReturn("리얼 소가죽");
        when(trade.quantity()).thenReturn(5_000L);
        return trade;
    }

    private void givenDraftBatch(Long batchId, String name, int producedQuantity, long totalUsed) {
        givenBatches(draft(batchId, name, producedQuantity, totalUsed, LATER));
    }

    /** 판매자가 발송해 오는 중인 거래. */
    private TradeResponse shippingTrade(Long id) {
        TradeResponse shipping = trade(id, TradeStatus.SHIPPED, 49L);
        when(shipping.createdAt()).thenReturn(EARLIER);
        when(shipping.shippedAt()).thenReturn(EARLIER);
        when(shipping.paidAt()).thenReturn(EARLIER);
        return shipping;
    }

    private void moveTo(Long conversationId, JourneyPhase phase) {
        var conversation = conversations.findById(conversationId).orElseThrow();
        conversation.moveTo(phase);
        conversations.save(conversation);
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
    /** 확정됐지만 체인 기록도 제품 연결도 안 된 배치. 여정이 멈추는 자리다. */
    private ProductionBatchResponse confirmed(Long batchId, String name, int producedQuantity) {
        ProductionBatchResponse batch = mock(ProductionBatchResponse.class);
        when(batch.productionBatchId()).thenReturn(batchId);
        when(batch.batchName()).thenReturn(name);
        when(batch.producedQuantity()).thenReturn(producedQuantity);
        when(batch.status()).thenReturn(ProductionBatchStatus.CONFIRMED);
        when(batch.chainBatchId()).thenReturn(null);
        when(batch.productModelId()).thenReturn(null);
        return batch;
    }

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

    /**
     * 이번 턴의 상태 블록 — 서버가 지금 상태를 읽어 만든 부분. 규칙 안내(첫 블록)는 빼고 본다. 남은 일이
     * 없으면 상태 블록이 아예 붙지 않으므로 빈 문자열이다.
     */
    private String liveState() {
        List<String> blocks = systemBlocks();
        return String.join(System.lineSeparator(), blocks.subList(1, blocks.size()));
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
