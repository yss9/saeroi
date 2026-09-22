package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.upcycle.agent.dto.ConversationDetailResponse;
import com.ssafy.upcycle.blockchain.service.ChainOrderStatusReader;
import com.ssafy.upcycle.agent.entity.AgentMessageRole;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.entity.PendingActionStatus;
import com.ssafy.upcycle.agent.repository.AgentConversationRepository;
import com.ssafy.upcycle.agent.repository.AgentMessageRepository;
import com.ssafy.upcycle.agent.repository.AgentPendingActionRepository;
import com.ssafy.upcycle.agent.service.AgentConversationService;
import com.ssafy.upcycle.agent.service.PendingActionService;
import com.ssafy.upcycle.agent.service.llm.LlmClient;
import com.ssafy.upcycle.agent.service.llm.LlmCompletion;
import com.ssafy.upcycle.agent.service.llm.ToolCall;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
import com.ssafy.upcycle.trade.service.TradeReceiptService;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 이미 지난 일에 대한 카드.
 *
 * <p><b>대화창은 도메인의 유일한 입구가 아니다.</b> 담당자는 같은 거래를 거래 상세 화면에서
 * 수령 확정하거나 취소할 수 있고, 그러면 대화는 그 사실을 모른다. 데이터가 깨지는 일은
 * 도메인이 막아 주지만, 담당자가 승인한 작업이 오류로 끝나는 것은 막아 주지 않는다.
 *
 * <p>실제로 완료된 거래에 온체인 기록 카드가 다시 떠 있는 것을 확인한 뒤 붙인 방어다.
 */
@SpringBootTest
class AgentStaleActionTests {

    @Autowired private AgentConversationService conversationService;
    @Autowired private PendingActionService pendingActionService;
    @Autowired private AgentConversationRepository conversations;
    @Autowired private AgentMessageRepository messages;
    @Autowired private AgentPendingActionRepository pendingActions;
    @Autowired private UserRepository users;

    @MockitoBean private LlmClient llmClient;
    @MockitoBean private MaterialTradeRepository trades;
    @MockitoBean private TradeReceiptService receiptService;
    /** 수령 확정은 체인에 수령이 남은 뒤에만 된다. 이 슬라이스에는 RPC 가 없다. */
    @MockitoBean private ChainOrderStatusReader chainOrders;

    private Long brandId;

    @BeforeEach
    void setUp() {
        brandId = users.save(User.register(
                        "agent-stale@test.com", "{noop}x", "brand", "010-0000-0000", UserRole.BRAND))
                .getId();
    }

    @AfterEach
    void clean() {
        pendingActions.deleteAll();
        messages.deleteAll();
        conversations.deleteAll();
        users.deleteAll();
    }

    @Test
    void 이미_체인에_기록된_거래에는_카드를_띄우지_않는다() {
        givenTrade(TradeStatus.COMPLETED, 26L, true);

        ConversationDetailResponse detail = callTool(
                "request_chain_order", "{\"tradeId\":15,\"summary\":\"온체인 기록\"}");

        assertThat(lastToolResult()).contains("ALREADY_DONE");
        // 카드를 만들어 두면 담당자가 누르고 나서야 실패한다. 애초에 띄우지 않는다.
        assertThat(detail.actions()).isEmpty();
        assertThat(pendingActions.count()).isZero();
    }

    @Test
    void 카드를_만든_뒤_화면에서_먼저_처리했으면_승인해도_실행되지_않는다() {
        // 카드를 띄울 때는 아직 발송 상태였다. 온체인 주문과 체인 수령이 있어야 카드가
        // 뜬다 — 수령 확정은 체인에 수령이 남은 뒤에만 되기 때문이다.
        givenTrade(TradeStatus.SHIPPED, 29L, false);
        when(chainOrders.alreadyReceived(29L)).thenReturn(true);
        callTool("confirm_receipt", "{\"tradeId\":15,\"summary\":\"수령 확정\"}");
        Long actionId = pendingActions.findAll().getFirst().getId();

        // 그 사이 담당자가 거래 상세 화면에서 수령을 확정했다.
        givenTrade(TradeStatus.RECEIVED, 29L, true);
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("확인했습니다.", List.of()));

        ConversationDetailResponse detail = pendingActionService.complete(brandId, actionId, null);

        assertThat(pendingActions.findById(actionId).orElseThrow().getStatus())
                .isEqualTo(PendingActionStatus.STALE);
        // 두 번 수령 확정되면 판매자에게 돈이 두 번 갈 수 있다. 도메인을 부르지 않아야 한다.
        verify(receiptService, never()).confirmReceipt(any(), any());
        // 담당자에게 "이미 끝났다" 를 말해 준다. 카드만 조용히 사라지면 무슨 일인지 모른다.
        assertThat(detail.messages())
                .anyMatch(message -> message.content() != null && message.content().contains("이미 수령이 확정"));
    }

    /**
     * <b>방금 한 서명은 "지난 일" 이 아니다.</b>
     *
     * <p>온체인 주문 서명은 서명자가 체인에 주문을 만든 뒤 chain-link API 로 서버에 붙이고
     * 나서 카드를 승인한다. 그래서 승인 시점에는 이미 {@code chainTradeId} 가 채워져 있다 —
     * 그 일을 한 것이 담당자 자신이다. 상태만 보고 판정하면 성공한 서명이 "이미 처리됨" 으로
     * 닫히고, 담당자는 서명이 헛일이었다고 읽는다. 실제로 그렇게 보였다.
     */
    @Test
    void 방금_한_서명이_자기_카드를_이미_처리됨으로_닫지_않는다() {
        // 카드를 띄울 때는 온체인 주문이 없었다.
        givenTrade(TradeStatus.PAID, null, false);
        callTool("request_chain_order", "{\"tradeId\":15,\"summary\":\"실크 원단 1kg 구매\"}");
        Long actionId = pendingActions.findAll().getFirst().getId();

        // 서명이 끝나 체인에 주문이 생기고 서버에도 붙었다.
        givenTrade(TradeStatus.PAID, 41L, false);
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("기록됐습니다.", List.of()));

        ConversationDetailResponse detail =
                pendingActionService.complete(brandId, actionId, "{\"txHash\":\"0xabc123\"}");

        assertThat(pendingActions.findById(actionId).orElseThrow().getStatus())
                .isEqualTo(PendingActionStatus.COMPLETED);
        assertThat(detail.messages())
                .anyMatch(message -> message.content() != null
                        && message.content().contains("블록체인에 기록됐습니다"));
        assertThat(detail.messages())
                .noneMatch(message -> message.content() != null
                        && message.content().contains("다시 서명할 필요가 없습니다"));
    }

    /**
     * 해시가 없는 승인은 그대로 판정한다.
     *
     * <p>화면이 {@code alreadyLinked} 로 닫는 건들이 이쪽이고, 그건 정말 남이 먼저 한
     * 경우다. 완화가 모든 승인으로 번지면 위의 방어가 사라진다.
     */
    @Test
    void 해시가_없는_승인은_이미_처리됨_판정을_그대로_받는다() {
        givenTrade(TradeStatus.PAID, null, false);
        callTool("request_chain_order", "{\"tradeId\":15,\"summary\":\"실크 원단 1kg 구매\"}");
        Long actionId = pendingActions.findAll().getFirst().getId();

        givenTrade(TradeStatus.PAID, 41L, false);
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("이미 끝났습니다.", List.of()));

        ConversationDetailResponse detail =
                pendingActionService.complete(brandId, actionId, "{\"alreadyLinked\":true}");

        assertThat(pendingActions.findById(actionId).orElseThrow().getStatus())
                .isEqualTo(PendingActionStatus.STALE);
        assertThat(detail.messages())
                .anyMatch(message -> message.content() != null
                        && message.content().contains("다시 서명할 필요가 없습니다"));
    }

    /** 스텁 거래 하나. 도메인 픽스처를 만들지 않는 이유는 여기서 볼 것이 판정 로직이기 때문이다. */
    private void givenTrade(TradeStatus status, Long chainTradeId, boolean received) {
        MaterialTrade trade = mock(MaterialTrade.class);
        when(trade.getBuyerId()).thenReturn(brandId);
        when(trade.getStatus()).thenReturn(status);
        when(trade.getChainTradeId()).thenReturn(chainTradeId);
        when(trade.getReceivedAt()).thenReturn(received ? java.time.LocalDateTime.now() : null);
        when(trades.findById(any())).thenReturn(java.util.Optional.of(trade));
    }

    private ConversationDetailResponse callTool(String toolName, String argumentsJson) {
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));
        if (conversations.count() == 0) {
            conversationService.start(brandId, "소재 사고 싶어요");
            var conversation = conversations.findAll().getFirst();
            conversation.moveTo(JourneyPhase.MATERIAL_SOURCING);
            conversations.save(conversation);
        }
        Long conversationId = conversations.findAll().getFirst().getId();

        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion(null, List.of(new ToolCall("c1", toolName, argumentsJson))))
                .thenReturn(new LlmCompletion("확인했습니다.", List.of()));

        return conversationService.send(brandId, conversationId, "진행해줘");
    }

    private String lastToolResult() {
        return messages.findAll().stream()
                .filter(message -> message.getRole() == AgentMessageRole.TOOL)
                .reduce((first, second) -> second)
                .orElseThrow()
                .getToolPayloadJson();
    }
}
