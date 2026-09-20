package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ssafy.upcycle.agent.dto.ConversationDetailResponse;
import com.ssafy.upcycle.agent.entity.AgentMessageRole;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.entity.PendingActionType;
import com.ssafy.upcycle.agent.repository.AgentConversationRepository;
import com.ssafy.upcycle.agent.repository.AgentMessageRepository;
import com.ssafy.upcycle.agent.repository.AgentPendingActionRepository;
import com.ssafy.upcycle.agent.service.AgentConversationService;
import com.ssafy.upcycle.agent.service.llm.LlmClient;
import com.ssafy.upcycle.agent.service.llm.LlmCompletion;
import com.ssafy.upcycle.agent.service.llm.ToolCall;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 수령을 체인에 기록하는 서명 카드.
 *
 * <p><b>이 서명이 체인에서 소유권을 옮기는 유일한 자리다.</b> 빠뜨리면 DB 는 담당자 소유인데
 * 체인은 판매자 잠금에 남는다 — 실제로 거래 두 건이 그 상태였다(체인 주문이 SHIPPED 에서
 * 멈춰 있었다). 화면에는 이 서명이 있었고 대화에만 없었다.
 */
@SpringBootTest
class AgentChainReceiptTests {

    @Autowired private AgentConversationService conversationService;
    @Autowired private AgentConversationRepository conversations;
    @Autowired private AgentMessageRepository messages;
    @Autowired private AgentPendingActionRepository pendingActions;
    @Autowired private UserRepository users;

    @MockitoBean private LlmClient llmClient;
    @MockitoBean private MaterialTradeRepository trades;

    private Long brandId;

    @BeforeEach
    void setUp() {
        brandId = users.save(User.register(
                        "agent-receipt@test.com", "{noop}x", "brand", "010-0000-0000", UserRole.BRAND))
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
    void 수령_확정이_끝난_거래에는_서명_카드가_뜬다() {
        givenTrade(29L, LocalDateTime.now());

        ConversationDetailResponse detail = callTool(
                "request_chain_receipt", "{\"tradeId\":16,\"summary\":\"수령 온체인 기록\"}");

        assertThat(detail.actions()).hasSize(1);
        assertThat(detail.actions().getFirst().actionType()).isEqualTo(PendingActionType.WALLET_SIGN);
    }

    @Test
    void 온체인에_없는_거래에는_띄우지_않는다() {
        // 판매 글이 온체인에 없으면 체인에 남길 수령 기록도 없다. 카드를 띄우면 담당자가
        // 눌러 보고 나서야 그 사실을 알게 된다.
        givenTrade(null, LocalDateTime.now());

        ConversationDetailResponse detail = callTool(
                "request_chain_receipt", "{\"tradeId\":16,\"summary\":\"수령 온체인 기록\"}");

        assertThat(lastToolResult()).contains("ALREADY_DONE");
        assertThat(detail.actions()).isEmpty();
    }

    @Test
    void 수령_확정_전에는_띄우지_않는다() {
        givenTrade(29L, null);

        callTool("request_chain_receipt", "{\"tradeId\":16,\"summary\":\"수령 온체인 기록\"}");

        assertThat(lastToolResult()).contains("confirm_receipt");
    }

    private void givenTrade(Long chainTradeId, LocalDateTime receivedAt) {
        MaterialTrade trade = mock(MaterialTrade.class);
        when(trade.getBuyerId()).thenReturn(brandId);
        when(trade.getChainTradeId()).thenReturn(chainTradeId);
        when(trade.getReceivedAt()).thenReturn(receivedAt);
        when(trades.findById(any())).thenReturn(Optional.of(trade));
    }

    private ConversationDetailResponse callTool(String toolName, String argumentsJson) {
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));
        conversationService.start(brandId, "소재 받았어요");
        var conversation = conversations.findAll().getFirst();
        conversation.moveTo(JourneyPhase.MATERIAL_SOURCING);
        conversations.save(conversation);

        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion(null, List.of(new ToolCall("c1", toolName, argumentsJson))))
                .thenReturn(new LlmCompletion("확인했습니다.", List.of()));

        return conversationService.send(brandId, conversation.getId(), "진행해줘");
    }

    private String lastToolResult() {
        return messages.findAll().stream()
                .filter(message -> message.getRole() == AgentMessageRole.TOOL)
                .reduce((first, second) -> second)
                .orElseThrow()
                .getToolPayloadJson();
    }
}
