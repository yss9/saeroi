package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.ssafy.upcycle.agent.dto.ConversationDetailResponse;
import com.ssafy.upcycle.agent.dto.PendingActionResponse;
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
import com.ssafy.upcycle.common.exception.BusinessException;
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

/** 확인 카드를 눌렀을 때의 경로 — 쓰기 tool 이 실제로 도는 유일한 지점. */
@SpringBootTest
class PendingActionServiceTests {

    @Autowired private AgentConversationService conversationService;
    @Autowired private PendingActionService pendingActionService;
    @Autowired private AgentConversationRepository conversations;
    @Autowired private AgentMessageRepository messages;
    @Autowired private AgentPendingActionRepository pendingActions;
    @Autowired private UserRepository users;

    @MockitoBean private LlmClient llmClient;

    private Long brandId;

    @BeforeEach
    void setUp() {
        brandId = users.save(User.register(
                        "action-brand@test.com", "{noop}x", "brand", "010-0000-0000", UserRole.BRAND))
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
    void 승인하면_tool_이_실행되고_대화가_이어진다() {
        PendingActionResponse action = proposeSignature();

        // 승인 뒤의 턴에서는 모델이 말로 답한다.
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("발행이 기록되었습니다. 다음으로 가공업체를 찾아볼까요?", List.of()));

        ConversationDetailResponse detail = pendingActionService.complete(
                brandId, action.id(), "{\"txHash\":\"0xabc1234567890def1234567890abcdef12345678\"}");

        assertThat(pendingActions.findById(action.id()).orElseThrow().getStatus())
                .isEqualTo(PendingActionStatus.COMPLETED);
        // 서명 결과가 대화에 남아야 담당자가 무엇이 끝났는지 알 수 있다.
        assertThat(detail.messages()).anyMatch(message ->
                message.content() != null && message.content().contains("블록체인에 기록"));
    }

    @Test
    void 거절은_오류가_아니라_선택이다() {
        PendingActionResponse action = proposeSignature();

        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("알겠습니다. 다른 방법을 찾아볼까요?", List.of()));

        ConversationDetailResponse detail = pendingActionService.reject(brandId, action.id());

        assertThat(pendingActions.findById(action.id()).orElseThrow().getStatus())
                .isEqualTo(PendingActionStatus.REJECTED);
        assertThat(detail.messages()).anyMatch(message ->
                message.content() != null && message.content().contains("취소"));
    }

    @Test
    void 같은_액션을_두_번_승인할_수_없다() {
        PendingActionResponse action = proposeSignature();
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("완료", List.of()));

        pendingActionService.complete(brandId, action.id(), "{\"txHash\":\"0xabc\"}");

        // 두 번 누르면 두 번 실행되는 것이 이 구조에서 가장 위험한 버그다.
        assertThatThrownBy(() ->
                        pendingActionService.complete(brandId, action.id(), "{\"txHash\":\"0xabc\"}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 처리되었거나");
    }

    @Test
    void 남의_액션은_건드릴_수_없다() {
        PendingActionResponse action = proposeSignature();

        Long otherBrandId = users.save(User.register(
                        "action-other@test.com", "{noop}x", "other", "010-1111-1111", UserRole.BRAND))
                .getId();

        assertThatThrownBy(() -> pendingActionService.complete(otherBrandId, action.id(), "{}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("대화를 찾을 수 없습니다");
        assertThat(pendingActions.findById(action.id()).orElseThrow().getStatus())
                .isEqualTo(PendingActionStatus.PENDING);
    }

    @Test
    void 대화를_종료하면_떠_있던_카드도_닫힌다() {
        PendingActionResponse action = proposeSignature();
        Long conversationId = conversations.findAll().getFirst().getId();

        conversationService.abandon(brandId, conversationId);

        assertThat(pendingActions.findById(action.id()).orElseThrow().getStatus())
                .isEqualTo(PendingActionStatus.EXPIRED);
    }

    /** 서명 카드가 하나 떠 있는 상태를 만든다. */
    private PendingActionResponse proposeSignature() {
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("네", List.of()));
        ConversationDetailResponse started = conversationService.start(brandId, "소재 발행할게요");

        var conversation = conversations.findById(started.id()).orElseThrow();
        conversation.moveTo(JourneyPhase.MATERIAL_SOURCING);
        conversations.save(conversation);

        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion(
                        null,
                        List.of(new ToolCall(
                                "call-1",
                                "request_chain_order",
                                "{\"tradeId\":1,\"summary\":\"소재 1kg 온체인 기록\"}"))))
                .thenReturn(new LlmCompletion("확인해 주세요.", List.of()));

        ConversationDetailResponse detail = conversationService.send(brandId, started.id(), "진행해줘");
        return detail.actions().getFirst();
    }
}
