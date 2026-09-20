package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ssafy.upcycle.agent.entity.AgentMessageRole;
import com.ssafy.upcycle.agent.repository.AgentConversationRepository;
import com.ssafy.upcycle.agent.repository.AgentMessageRepository;
import com.ssafy.upcycle.agent.repository.AgentPendingActionRepository;
import com.ssafy.upcycle.agent.service.AgentConversationService;
import com.ssafy.upcycle.agent.service.llm.LlmClient;
import com.ssafy.upcycle.agent.service.llm.LlmCompletion;
import com.ssafy.upcycle.trade.dto.PageResponse;
import com.ssafy.upcycle.trade.dto.TradeResponse;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import com.ssafy.upcycle.trade.service.TradeQueryService;
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
 * 대화 밖 사건을 알릴 때 <b>지금 상태</b>를 함께 넣는가.
 *
 * <p>모델이 이력에 남은 낡은 조회 결과를 믿고 사실과 다르게 설명한 일이 있었다. 결제 전에
 * 받은 {@code needsChainOrder:false} 를 근거로 "온체인 기록이 남지 않는 거래" 라고 말했는데,
 * 판매 글은 온체인에 등록돼 있었고 거래는 서명만 기다리는 상태였다.
 *
 * <p>프롬프트에 "결제 후 곧바로 조회하라" 고 적어도 경량 모델은 지키지 않는다. 그래서 서버가
 * 넣어 준다 — 모델이 기억하지 않아도 눈앞에 있게.
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

    @Test
    void 결제_복귀를_알릴_때_남은_서명을_함께_알려준다() {
        givenTrades(trade(16L, TradeStatus.PAID, null));

        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));
        var detail = conversationService.start(brandId, "소재 사고 싶어요");

        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("온체인 기록을 남기겠습니다.", List.of()));
        conversationService.reportEvent(brandId, detail.id(), "결제가 완료되었습니다 (거래 16번).");

        // 서버가 붙인 현재 상태가 이력에 들어가 모델이 보게 된다.
        String note = eventNote();
        assertThat(note).contains("request_chain_order");
        assertThat(note).contains("이 목록이 정본이다");
    }

    @Test
    void 할_일이_없으면_아무것도_덧붙이지_않는다() {
        // 완료된 거래만 있으면 손이 필요한 일이 없다. 빈 목록을 붙이면 입력 토큰만 늘고,
        // "없음" 을 읽은 모델이 할 일을 지어낼 여지가 생긴다.
        givenTrades(trade(15L, TradeStatus.COMPLETED, 26L));

        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));
        var detail = conversationService.start(brandId, "안녕");

        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("확인했습니다.", List.of()));
        conversationService.reportEvent(brandId, detail.id(), "결제가 완료되었습니다 (거래 15번).");

        assertThat(eventNote()).isEqualTo("결제가 완료되었습니다 (거래 15번).");
    }

    private TradeResponse trade(Long id, TradeStatus status, Long chainTradeId) {
        TradeResponse trade = mock(TradeResponse.class);
        when(trade.tradeId()).thenReturn(id);
        when(trade.status()).thenReturn(status);
        when(trade.chainTradeId()).thenReturn(chainTradeId);
        when(trade.materialName()).thenReturn("리얼 소가죽");
        return trade;
    }

    private void givenTrades(TradeResponse... trades) {
        when(tradeQuery.findMyTrades(any(), any()))
                .thenReturn(new PageResponse<>(List.of(trades), 0, 10, trades.length, 1, false));
    }

    /** 사건 보고로 들어간 줄. 모델의 답변이 아니라 서버가 넣은 것이다. */
    private String eventNote() {
        return messages.findAll().stream()
                .filter(message -> message.getRole() == AgentMessageRole.ASSISTANT)
                .map(message -> message.getContent())
                .filter(content -> content != null && content.startsWith("결제가 완료되었습니다"))
                .findFirst()
                .orElseThrow();
    }
}
