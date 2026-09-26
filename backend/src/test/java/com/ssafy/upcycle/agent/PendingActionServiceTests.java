package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.upcycle.agent.AgentErrorCode;
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
import com.ssafy.upcycle.agent.service.llm.LlmMessage;
import com.ssafy.upcycle.agent.service.llm.LlmRole;
import com.ssafy.upcycle.agent.service.llm.ToolCall;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
        // **결과는 모델이 한 번만 말한다.** 예전에는 서버가 결과 문장을 이력에 남기고 모델이
        // 곧이어 같은 말을 또 해서, 수령 세 건에 "수령이 블록체인에 기록되고 확정됐습니다" 가
        // 네 번 떴다. 지금은 서버 문장이 이번 턴의 상태 블록으로만 들어가고 화면에는 모델이
        // 쓴 문장 하나만 남는다.
        assertThat(detail.messages()).anyMatch(message ->
                message.content() != null && message.content().contains("발행이 기록되었습니다"));
        assertThat(detail.messages())
                .as("서버가 쓴 결과 문장이 화면에 그대로 나가면 모델의 말과 겹친다")
                .noneMatch(message -> message.content() != null
                        && message.content().contains("주문이 블록체인에 기록됐습니다"));
    }

    @Test
    void 거절은_오류가_아니라_선택이다() {
        PendingActionResponse action = proposeSignature();

        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion("알겠습니다. 온체인 기록은 멈춰 둘게요. 어떻게 할까요?", List.of()));

        ConversationDetailResponse detail = pendingActionService.reject(brandId, action.id());

        assertThat(pendingActions.findById(action.id()).orElseThrow().getStatus())
                .isEqualTo(PendingActionStatus.REJECTED);
        // **모델이 말하고, 서버는 문장을 따로 남기지 않는다.** 예전에는 서버가 "담당자가 이 작업을
        // 취소했습니다." 를 먼저 쓰고 모델이 또 말해, 담당자는 무엇을 취소했는지도 없는 문장을 따로 봤다.
        assertThat(detail.messages().getLast().content()).isEqualTo("알겠습니다. 온체인 기록은 멈춰 둘게요. 어떻게 할까요?");
        assertThat(detail.messages()).noneMatch(message ->
                message.content() != null && message.content().contains("카드를 취소했습니다"));

        // 무엇을 취소했는지는 모델에게 간다 — 같은 카드를 다시 띄우지 않게 한다.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LlmMessage>> prompts = ArgumentCaptor.forClass(List.class);
        verify(llmClient, atLeastOnce()).complete(prompts.capture(), anyList());
        assertThat(prompts.getValue())
                .filteredOn(message -> message.role() == LlmRole.SYSTEM)
                .anyMatch(message -> message.content().contains("\"" + action.summary() + "\" 카드를 취소했습니다")
                        && message.content().contains("그 카드를 다시 띄우지 않는다"));
    }

    @Test
    void 거절_뒤_모델이_말하지_못하면_무엇을_취소했는지_서버가_한_번_남긴다() {
        PendingActionResponse action = proposeSignature();

        when(llmClient.complete(anyList(), anyList())).thenThrow(new IllegalStateException("llm down"));

        ConversationDetailResponse detail = pendingActionService.reject(brandId, action.id());

        assertThat(detail.messages())
                .filteredOn(message -> message.content() != null && message.content().contains("카드를 취소했습니다"))
                .singleElement()
                .satisfies(message -> assertThat(message.content()).contains(action.summary()));
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
    /**
     * 승인 뒤 모델이 말을 잇지 못해도 <b>승인은 성공이다.</b>
     *
     * <p>승인의 DB 작업은 이미 커밋된 뒤이고, 그 다음 LLM 호출은 "모델이 한마디 덧붙이는
     * 것" 이라 본질이 다르다. 예외를 그대로 올리면 이미 끝난 작업이 화면에서 실패로 보인다.
     *
     * <p>실제로 그랬다. 생산 배치 취소 카드를 승인해 배치가 정상적으로 취소됐는데, 이어진
     * 호출이 빈 응답을 받아 요청 전체가 AGENT_LLM_UNAVAILABLE 로 끝났다. 담당자는
     * "처리하지 못했습니다" 를 봤지만 배치는 이미 취소돼 있었고, 다시 누르면 이번에는
     * 도메인이 "이미 취소된 배치" 라며 거절한다 — 무엇이 참인지 알 방법이 없어진다.
     */
    @Test
    void 승인_뒤_모델이_말을_잇지_못해도_승인은_성공이다() {
        PendingActionResponse action = proposeSignature();

        // 승인 직후의 이어가기 호출만 실패시킨다.
        when(llmClient.complete(anyList(), anyList()))
                .thenThrow(new BusinessException(AgentErrorCode.AGENT_LLM_UNAVAILABLE));

        ConversationDetailResponse after = pendingActionService.complete(
                brandId, action.id(), "{\"txHash\":\"0xabc123\"}");

        // 카드는 완료로 닫혀야 한다. 다시 누르게 두면 도메인이 거절하고 담당자는 혼란에 빠진다.
        assertThat(after.actions())
                .anySatisfy(each -> {
                    assertThat(each.id()).isEqualTo(action.id());
                    assertThat(each.status()).isEqualTo(PendingActionStatus.COMPLETED);
                });
        // 조용히 넘기지 않는다 — 반응 없는 화면도 실패처럼 읽힌다.
        //
        // **무슨 일이 있었는지를 적는다.** 예전에는 "작업은 정상적으로 처리됐습니다" 라는
        // 일반 문구였는데, 담당자는 그 말로는 무엇이 끝났는지 알 수 없었다. 모델이 말하지
        // 못하면 서버가 아는 결과 문장이 그대로 나간다.
        assertThat(after.messages())
                .anyMatch(message -> message.content() != null
                        && message.content().contains("블록체인에 기록"));
    }

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
