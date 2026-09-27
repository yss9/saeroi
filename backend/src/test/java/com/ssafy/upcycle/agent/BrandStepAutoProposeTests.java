package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ssafy.upcycle.agent.dto.ConversationDetailResponse;
import com.ssafy.upcycle.agent.entity.AgentPendingAction;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.entity.PendingActionStatus;
import com.ssafy.upcycle.agent.entity.PendingActionType;
import com.ssafy.upcycle.agent.repository.AgentConversationRepository;
import com.ssafy.upcycle.agent.repository.AgentMessageRepository;
import com.ssafy.upcycle.agent.repository.AgentPendingActionRepository;
import com.ssafy.upcycle.agent.service.AgentConversationService;
import com.ssafy.upcycle.agent.service.AgentToolExecutor;
import com.ssafy.upcycle.agent.service.llm.LlmClient;
import com.ssafy.upcycle.agent.service.llm.ToolCall;
import com.ssafy.upcycle.agent.service.llm.LlmCompletion;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * 담당자 차례가 되면 <b>말을 걸지 않아도 카드가 뜬다.</b>
 *
 * <h2>왜 필요한가</h2>
 *
 * <p>공급업체가 발송하고 가공업체가 수락해도 대화창은 조용했다. 진행 막대와 오른쪽 목록만
 * 바뀌었고, 담당자가 그 영역을 보고 있지 않으면 놓쳤다.
 *
 * <p>"담당자가 물어보면 되지 않느냐" 가 성립하지 않는다 — <b>물어볼 수가 없다.</b> 온체인
 * 등록이나 인계 서명 같은 단계는 구현 세부이지 담당자가 아는 절차가 아니다. 실제로
 * 가공업체가 수락한 뒤 담당자가 "소재 발송했어" 라고 먼저 꺼내야 진행됐다.
 *
 * <p>화면이 20초마다 부르는 경과 조회에 얹었다. 모델을 부르지 않으므로 크레딧이 들지 않는다.
 */
@SpringBootTest
class BrandStepAutoProposeTests {

    @Autowired private AgentConversationService conversationService;
    @Autowired private AgentConversationRepository conversations;
    @Autowired private AgentMessageRepository messages;
    @Autowired private AgentPendingActionRepository pendingActions;
    @Autowired private UserRepository users;

    @MockitoBean private LlmClient llmClient;
    @MockitoBean private TradeQueryService tradeQuery;

    /**
     * 실물을 그대로 쓰되 <b>한 검사만</b> 갈아끼운다. 도구가 막는 상황을 실제로 만들려면
     * 체인 응답까지 꾸며야 하는데, 여기서 보려는 것은 "막히면 띄우지 않는가" 라는 이음매다.
     */
    @MockitoSpyBean private AgentToolExecutor executor;

    /** 모델 경로를 그대로 부른다 — 중복은 그 문으로 들어왔다. */
    @Autowired private AgentToolExecutor toolExecutor;

    private Long brandId;
    private Long conversationId;

    @BeforeEach
    void setUp() {
        brandId = users.save(User.register(
                        "agent-auto@test.com", "{noop}x", "brand", "010-0000-0000", UserRole.BRAND))
                .getId();
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));
        conversationId = conversationService.start(brandId, "소재 사고 싶어요").id();

        var conversation = conversations.findById(conversationId).orElseThrow();
        conversation.moveTo(JourneyPhase.MATERIAL_SOURCING);
        conversations.save(conversation);

        // 거래 27 은 이 대화에서 산 것이다. 자동 카드는 이 대화가 시작한 일에만 뜬다.
        ProjectCards.boughtTrade(pendingActions, conversationId, 27L);
    }

    @AfterEach
    void clean() {
        pendingActions.deleteAll();
        messages.deleteAll();
        conversations.deleteAll();
        users.deleteAll();
    }

    /** 판매자가 발송하면 수령 서명 카드가 <b>스스로</b> 뜬다. */
    @Test
    void 상대가_움직이면_서명_카드가_스스로_뜬다() {
        givenTrades(trade(27L, TradeStatus.SHIPPED, 45L));

        var progress = conversationService.progress(brandId, conversationId);

        assertThat(progress.conversationChanged())
                .as("화면이 대화를 다시 읽어야 새 카드가 보인다")
                .isTrue();

        ConversationDetailResponse detail = conversationService.detail(brandId, conversationId);
        var open = detail.actions().stream()
                .filter(action -> action.status() == PendingActionStatus.PENDING)
                .toList();
        assertThat(open).hasSize(1);
        assertThat(open.getFirst().toolName()).isEqualTo("confirm_receipt");
        assertThat(open.getFirst().actionType()).isEqualTo(PendingActionType.WALLET_SIGN);
        // 카드만 불쑥 뜨면 왜 떴는지 알 수 없다. 대화에 한 줄이 함께 남는다.
        assertThat(detail.messages())
                .anyMatch(message -> message.content() != null
                        && message.content().contains("수령을 확정할 차례입니다"));
    }

    /** 폴링마다 불린다. 같은 카드가 쌓이면 안 된다. */
    @Test
    void 같은_카드를_두_번_띄우지_않는다() {
        givenTrades(trade(27L, TradeStatus.SHIPPED, 45L));

        conversationService.progress(brandId, conversationId);
        var second = conversationService.progress(brandId, conversationId);

        assertThat(second.conversationChanged()).isFalse();
        assertThat(openCards()).isEqualTo(1);
    }

    /**
     * <b>담당자가 취소한 일은 다시 띄우지 않는다.</b>
     *
     * <p>취소해도 남은 일 목록에는 그대로 남는다 — 수령을 취소해도 거래는 여전히 발송됨이다.
     * 그래서 폴링마다 같은 카드가 새로 떴다. 0원 판매 시작 카드를 세 번 취소했는데도 계속 떠
     * 결국 승인됐다.
     */
    @Test
    void 담당자가_취소한_일은_다시_띄우지_않는다() {
        givenTrades(trade(27L, TradeStatus.SHIPPED, 45L));
        AgentPendingAction canceled = AgentPendingAction.propose(
                conversationId,
                messages.findByConversationIdOrderBySequenceNoAsc(conversationId).getFirst().getId(),
                PendingActionType.WALLET_SIGN,
                "confirm_receipt",
                "폐데님 원단 수령 확정",
                "{\"tradeId\":27,\"summary\":\"폐데님 원단 수령 확정\"}",
                1,
                LocalDateTime.now().plusHours(1));
        canceled.reject(LocalDateTime.now());
        pendingActions.save(canceled);

        var progress = conversationService.progress(brandId, conversationId);

        assertThat(progress.conversationChanged()).isFalse();
        assertThat(openCards()).isZero();
    }

    /** 취소한 것이 다른 거래라면 막지 않는다. */
    @Test
    void 다른_거래를_취소했으면_막지_않는다() {
        givenTrades(trade(27L, TradeStatus.SHIPPED, 45L));
        AgentPendingAction canceled = AgentPendingAction.propose(
                conversationId,
                messages.findByConversationIdOrderBySequenceNoAsc(conversationId).getFirst().getId(),
                PendingActionType.WALLET_SIGN,
                "confirm_receipt",
                "다른 원단 수령 확정",
                "{\"tradeId\":28,\"summary\":\"다른 원단 수령 확정\"}",
                1,
                LocalDateTime.now().plusHours(1));
        canceled.reject(LocalDateTime.now());
        pendingActions.save(canceled);

        conversationService.progress(brandId, conversationId);

        assertThat(openCards()).isEqualTo(1);
    }

    /**
     * 기한이 지난 카드는 폴링이 닫는다.
     *
     * <p>기한은 이미 모든 쓰기 경로가 존중한다 — 승인하려 하면 만료로 돌려세운다. 그런데
     * 읽기 경로는 그 말을 하지 않아, 상태가 {@code PENDING} 인 채로 화면에 멀쩡한 카드로
     * 남았다. 담당자는 눌러 본 뒤에야 기한이 지났다는 것을 알았다.
     */
    @Test
    void 기한이_지난_카드는_폴링이_닫는다() {
        givenTrades();
        pendingActions.save(AgentPendingAction.propose(
                conversationId,
                messages.findByConversationIdOrderBySequenceNoAsc(conversationId).getFirst().getId(),
                PendingActionType.WALLET_SIGN,
                "request_custody_transfer",
                "가공 작업 5 소재 인계",
                "{\"jobId\":5,\"summary\":\"가공 작업 5 소재 인계\"}",
                1,
                LocalDateTime.now().minusMinutes(1)));

        var progress = conversationService.progress(brandId, conversationId);

        assertThat(progress.conversationChanged())
                .as("카드가 사라졌으니 화면이 대화를 다시 읽어야 한다")
                .isTrue();
        assertThat(conversationService.detail(brandId, conversationId).actions())
                .allMatch(action -> action.status() != PendingActionStatus.PENDING);
    }

    /**
     * 자동으로 띄운 카드가 있으면 <b>모델도 같은 카드를 만들지 못한다.</b>
     *
     * <p>카드를 만드는 문이 둘인데 중복 방지를 새 문에만 달았더니, 서버가 온체인 기록
     * 카드를 띄운 80초 뒤 모델이 같은 거래에 한 장을 더 만들었다. 담당자는 한쪽만 눌렀고
     * 나머지는 서명이 다 끝난 뒤에도 "지갑 서명이 필요합니다" 로 계속 떠 있었다.
     */
    @Test
    void 자동으로_띄운_카드를_모델이_또_띄우지_못한다() {
        givenTrades(trade(27L, TradeStatus.SHIPPED, 45L));
        conversationService.progress(brandId, conversationId);
        assertThat(openCards()).isEqualTo(1);

        // 모델이 같은 거래에 대해 같은 도구를 부른다.
        var conversation = conversations.findById(conversationId).orElseThrow();
        String result = toolExecutor.execute(
                conversation,
                brandId,
                new ToolCall("call-1", "confirm_receipt",
                        "{\"tradeId\":27,\"summary\":\"깨끗한 데님 6kg 수령 확정\"}"),
                messages.findByConversationIdOrderBySequenceNoAsc(conversationId).getFirst(),
                java.time.Duration.ofHours(6));

        assertThat(openCards())
                .as("같은 일에 카드가 두 장 생기면 담당자는 한쪽만 누른다")
                .isEqualTo(1);
        assertThat(result).contains("AWAITING_USER_CONFIRMATION");
        assertThat(result).contains("다시 띄우지 마세요");
    }

    /** 다른 거래라면 막지 않는다. 소재를 여러 건 사면 거래마다 카드가 필요하다. */
    @Test
    void 다른_거래의_카드는_막지_않는다() {
        givenTrades(trade(27L, TradeStatus.SHIPPED, 45L));
        conversationService.progress(brandId, conversationId);

        var conversation = conversations.findById(conversationId).orElseThrow();
        toolExecutor.execute(
                conversation,
                brandId,
                new ToolCall("call-2", "confirm_receipt",
                        "{\"tradeId\":28,\"summary\":\"폴리에스터 원단 수령 확정\"}"),
                messages.findByConversationIdOrderBySequenceNoAsc(conversationId).getFirst(),
                java.time.Duration.ofHours(6));

        assertThat(openCards()).isEqualTo(2);
    }

    /**
     * 도구가 아직 이르다고 하면 띄우지 않는다.
     *
     * <p>남은 일 목록은 DB 만 보고 판정한다. 그런데 어떤 단계는 체인을 읽어야 알 수 있다 —
     * 소재 인계가 그렇다. DB 가 ACCEPTED 여도 업체가 체인에서 수락하기 전이면 서명할 수
     * 없고, 모르고 서명하면 컨트랙트가 되돌린다. 그래서 모델이 띄울 때와 같은 검사를 지난다.
     *
     * <p>막혔을 때 대화에 아무것도 남기지 않는 것도 함께 본다. 폴링마다 "아직 안 됩니다" 를
     * 남기면 대화가 그것으로 덮인다.
     */
    @Test
    void 도구가_막으면_카드를_띄우지_않는다() {
        givenTrades(trade(27L, TradeStatus.SHIPPED, 45L));
        when(executor.mayPropose(any(), any(), any(), any())).thenReturn(false);

        var progress = conversationService.progress(brandId, conversationId);

        assertThat(progress.conversationChanged()).isFalse();
        assertThat(openCards()).isZero();
        assertThat(conversationService.detail(brandId, conversationId).messages())
                .as("막힌 이유를 폴링마다 남기면 대화가 그것으로 덮인다")
                .noneMatch(message -> message.content() != null
                        && message.content().contains("카드를 띄웠습니다"));
    }

    /**
     * 한 단계가 터져도 폴링은 살아 있어야 한다.
     *
     * <p>이 응답에는 진행 막대도 함께 실려 있다. 카드 하나 때문에 예외가 올라가면 화면
     * 위쪽이 통째로 비고, 20초마다 도는 경로라 한 번이 아니라 계속 그렇다.
     */
    @Test
    void 한_단계가_터져도_폴링은_살아_있다() {
        givenTrades(trade(27L, TradeStatus.SHIPPED, 45L));
        when(executor.mayPropose(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("사전 검사가 흔들렸다"));

        var progress = conversationService.progress(brandId, conversationId);

        assertThat(progress).isNotNull();
        assertThat(progress.conversationChanged()).isFalse();
        assertThat(openCards()).isZero();
    }

    /**
     * <b>이 대화가 시작하지 않은 거래에는 띄우지 않는다.</b>
     *
     * <p>담당자가 부르지 않았는데 뜨는 카드라 범위가 가장 좁아야 한다. 홈페이지에서 산 소재가
     * 발송됐다고 에코백을 만드는 대화에 수령 카드가 뜨면, 담당자는 이것이 이번 제품 얘기인지 알
     * 수 없다. 그 거래도 담당자가 물으면 모델이 처리한다 — 먼저 꺼내지 않을 뿐이다.
     */
    @Test
    void 이_대화가_시작하지_않은_거래에는_카드를_띄우지_않는다() {
        givenTrades(trade(99L, TradeStatus.SHIPPED, 45L));

        var progress = conversationService.progress(brandId, conversationId);

        assertThat(progress.conversationChanged()).isFalse();
        assertThat(openCards()).isZero();
    }

    /**
     * 담당자에게 물어야 채워지는 인자가 있으면 띄우지 않는다.
     *
     * <p>결제 전 거래는 무엇을 얼마나 살지가 대화로 정해진 뒤에야 카드가 된다. 상태만 보고
     * 띄우면 담당자가 합의한 적 없는 내용에 결제창이 열린다.
     */
    @Test
    void 인자를_물어야_하는_단계는_자동으로_띄우지_않는다() {
        givenTrades(trade(27L, TradeStatus.CREATED, null));

        var progress = conversationService.progress(brandId, conversationId);

        assertThat(progress.conversationChanged()).isFalse();
        assertThat(openCards()).isZero();
    }

    private TradeResponse trade(Long id, TradeStatus status, Long chainTradeId) {
        TradeResponse trade = mock(TradeResponse.class);
        when(trade.tradeId()).thenReturn(id);
        when(trade.status()).thenReturn(status);
        when(trade.chainTradeId()).thenReturn(chainTradeId);
        when(trade.materialName()).thenReturn("폐데님 원단");
        return trade;
    }

    /** 떠 있는 카드 수. 이 대화에서 산 거래의 구매 카드는 이미 승인돼 세지 않는다. */
    private long openCards() {
        return pendingActions.findAll().stream()
                .filter(action -> action.getStatus() == PendingActionStatus.PENDING)
                .count();
    }

    private void givenTrades(TradeResponse... trades) {
        when(tradeQuery.findMyTrades(any(), any()))
                .thenReturn(new PageResponse<>(List.of(trades), 0, 10, trades.length, 1, false));
    }
}
