package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.upcycle.agent.dto.ConversationDetailResponse;
import com.ssafy.upcycle.agent.entity.AgentMessage;
import com.ssafy.upcycle.agent.entity.AgentMessageRole;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.entity.PendingActionStatus;
import com.ssafy.upcycle.agent.entity.PendingActionType;
import com.ssafy.upcycle.agent.repository.AgentConversationRepository;
import com.ssafy.upcycle.agent.repository.AgentMessageRepository;
import com.ssafy.upcycle.agent.repository.AgentPendingActionRepository;
import com.ssafy.upcycle.agent.service.AgentChatService;
import com.ssafy.upcycle.agent.service.AgentConversationService;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 에이전트 턴 오케스트레이션.
 *
 * <p>{@link LlmClient} 를 스텁으로 갈아끼운다. 진짜 모델을 부르면 같은 입력에 다른 출력이
 * 나와 테스트가 흔들리고, 여기서 확인하려는 것은 모델의 판단이 아니라 <b>모델이 무엇을 하든
 * 우리 쪽이 지키는 것</b>이기 때문이다.
 */
@SpringBootTest
class AgentChatServiceTests {

    @Autowired private AgentConversationService conversationService;
    @Autowired private AgentChatService chatService;
    @Autowired private AgentConversationRepository conversations;
    @Autowired private AgentMessageRepository messages;
    @Autowired private AgentPendingActionRepository pendingActions;
    @Autowired private UserRepository users;

    @MockitoBean private LlmClient llmClient;

    private Long brandId;

    @BeforeEach
    void setUp() {
        brandId = users.save(User.register(
                        "agent-brand@test.com", "{noop}x", "brand", "010-0000-0000", UserRole.BRAND))
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
    void 대화를_시작하면_사용자_발화와_모델_답변이_남는다() {
        answerWith("어떤 제품을 만들고 싶으신가요?");

        ConversationDetailResponse detail = conversationService.start(brandId, "데님으로 에코백 만들고 싶어요");

        assertThat(detail.phase()).isEqualTo(JourneyPhase.INTAKE);
        assertThat(detail.messages()).hasSize(2);
        assertThat(detail.messages().get(0).role()).isEqualTo(AgentMessageRole.USER);
        assertThat(detail.messages().get(1).role()).isEqualTo(AgentMessageRole.ASSISTANT);
        assertThat(detail.messages().get(1).content()).isEqualTo("어떤 제품을 만들고 싶으신가요?");
    }

    @Test
    void 읽기_tool_은_바로_실행되고_결과가_모델에게_돌아간다() {
        // 1회차: tool 을 부른다. 2회차: 결과를 받아 말로 답한다.
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion(
                        null, List.of(new ToolCall("call-1", "get_my_materials", "{}"))))
                .thenReturn(new LlmCompletion("보유하신 소재가 없습니다.", List.of()));

        ConversationDetailResponse detail = conversationService.start(brandId, "내 소재 보여줘");

        // 대화는 INTAKE 에 있지만 그대로 실행된다. 도구는 단계로 가리지 않는다
        // (AgentToolRegistry 참고) — 예전에는 여기서 거절됐고, 단계를 옮기지 않은 모델이
        // 할 수 있는 일을 "못 한다" 고 답했다.
        assertThat(detail.phase()).isEqualTo(JourneyPhase.INTAKE);
        assertThat(lastToolResult()).contains("보유한 소재가 없습니다").doesNotContain("error");
        assertThat(detail.messages().getLast().content()).isEqualTo("보유하신 소재가 없습니다.");
    }

    /**
     * 카드 두 장을 연달아 누르면 첫 턴이 도는 동안 둘째 결과가 도착해, 첫 턴 뒤에 한 턴이 더 돈다. 첫 턴이 이미 두 결과를
     * 다 말했으면 뒤 턴은 아무것도 남기지 않는다 — "가공이 모두 완료됐습니다…" 뒤에 "가공 작업 8, 9 모두
     * 완료됐습니다…" 가 또 떴다.
     */
    @Test
    void 뒤이어_말할_것이_없으면_아무것도_남기지_않는다() {
        answerWith("무엇을 만들까요?");
        Long id = conversationService.start(brandId, "지갑 만들래").id();
        List<List<LlmMessage>> prompts = new java.util.ArrayList<>();
        when(llmClient.complete(anyList(), anyList()))
                .thenAnswer(invocation -> {
                    prompts.add(List.copyOf(invocation.getArgument(0)));
                    // 첫 턴이 도는 동안 둘째 카드가 끝난다.
                    chatService.resumeAfterAction(brandId, id, "폐데님 원단 5kg 가공 결과 승인이 끝났습니다.");
                    return new LlmCompletion("두 가공이 모두 끝났습니다. 이제 생산을 시작할 수 있습니다.", List.of());
                })
                .thenAnswer(invocation -> {
                    prompts.add(List.copyOf(invocation.getArgument(0)));
                    return new LlmCompletion("[[조용히]]", List.of());
                });

        chatService.resumeAfterAction(brandId, id, "울 원단 5kg 가공 결과 승인이 끝났습니다.");

        List<String> said = messages.findByConversationIdOrderBySequenceNoAsc(id).stream()
                .filter(message -> message.getRole() == AgentMessageRole.ASSISTANT && !message.isToolCall())
                .map(AgentMessage::getContent)
                .toList();
        assertThat(said).containsExactly("무엇을 만들까요?", "두 가공이 모두 끝났습니다. 이제 생산을 시작할 수 있습니다.");
        // 뒤 턴은 조용히 끝나도 된다는 것과 무엇이 끝났는지를 받았다.
        assertThat(prompts).hasSize(2);
        assertThat(prompts.get(1))
                .filteredOn(message -> message.role() == LlmRole.SYSTEM)
                .anyMatch(message -> message.content().contains("뒤이어 말하기")
                        && message.content().contains("폐데님 원단 5kg 가공 결과 승인이 끝났습니다."));
    }

    @Test
    void 뒤이어_말할_것이_있으면_그것만_남긴다() {
        answerWith("무엇을 만들까요?");
        Long id = conversationService.start(brandId, "지갑 만들래").id();
        when(llmClient.complete(anyList(), anyList()))
                .thenAnswer(invocation -> {
                    chatService.resumeAfterAction(brandId, id, "폐데님 원단 5kg 소재 보내기 서명이 끝났습니다.");
                    return new LlmCompletion("울 원단을 업체로 보냈습니다.", List.of());
                })
                .thenReturn(new LlmCompletion("[[조용히]] 폐데님 원단도 보냈습니다. 이제 업체가 받기를 기다립니다.", List.of()));

        chatService.resumeAfterAction(brandId, id, "울 원단 5kg 소재 보내기 서명이 끝났습니다.");

        assertThat(messages.findByConversationIdOrderBySequenceNoAsc(id).getLast().getContent())
                .isEqualTo("폐데님 원단도 보냈습니다. 이제 업체가 받기를 기다립니다.");
    }

    /** 담당자의 말에 답하는 턴은 조용히 끝날 수 없다. 표시가 와도 그대로 남긴다 — 답이 없는 것보다 낫다. */
    @Test
    void 담당자에게_답하는_턴은_조용히_끝나지_않는다() {
        answerWith("[[조용히]]");

        ConversationDetailResponse detail = conversationService.start(brandId, "지갑 만들래");

        assertThat(detail.messages().getLast().content()).isEqualTo("[[조용히]]");
    }

    @Test
    void 한_번의_답에_도구를_둘_부르면_글은_첫_호출에만_남는다() {
        // 면을 폐소재·신소재로 한꺼번에 찾은 답이었다. 글을 호출마다 붙이면 이력에 같은 말이
        // 두 번 남아, 모델은 자기가 같은 말을 되풀이한 것으로 읽는다.
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion(
                        "면 소재를 찾아보겠습니다.",
                        List.of(
                                new ToolCall("call-1", "get_my_materials", "{}"),
                                new ToolCall("call-2", "get_my_materials", "{}"))))
                .thenReturn(new LlmCompletion("보유하신 소재가 없습니다.", List.of()));

        conversationService.start(brandId, "내 소재 보여줘");

        assertThat(messages.findAll().stream()
                        .filter(AgentMessage::isToolCall)
                        .map(AgentMessage::getContent))
                .containsExactly("면 소재를 찾아보겠습니다.", null);
    }

    @Test
    void 쓰기_tool_은_실행되지_않고_확인_카드만_만든다() {
        // 이 테스트가 이 기능의 핵심 안전 장치다. 모델이 쓰기 tool 을 불러도 도메인은
        // 움직이지 않아야 하고, 사용자가 누를 카드만 생겨야 한다.
        //
        // 대화 단계를 옮기지 않은 채(INTAKE) 부른다. 단계는 도구를 막지 않고, 막는 것은
        // 이 카드다.
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion(
                        null,
                        List.of(new ToolCall(
                                "call-1",
                                "request_chain_order",
                                "{\"tradeId\":1,\"summary\":\"소재 구매 온체인 기록\"}"))))
                .thenReturn(new LlmCompletion("확인해 주세요.", List.of()));

        ConversationDetailResponse detail = conversationService.start(brandId, "발행 진행해줘");

        assertThat(detail.actions()).hasSize(1);
        assertThat(detail.actions().getFirst().actionType()).isEqualTo(PendingActionType.WALLET_SIGN);
        assertThat(detail.actions().getFirst().status()).isEqualTo(PendingActionStatus.PENDING);
        // 모델에게 "아직 안 끝났다" 고 분명히 알려줘야 완료로 착각하지 않는다.
        assertThat(lastToolResult()).contains("AWAITING_USER_CONFIRMATION");
        // 카드에 실제로 적힌 내용을 모델에게 돌려준다. 합의와 다른 카드를 띄웠을 때 모델이 알아챌 자리다.
        assertThat(lastToolResult()).contains("\"cardSummary\"");
    }

    /**
     * 카드를 띄운 턴은 도구 없이 한 번 더 불러 말로 끝낸다. 도구를 열어 두면 모델은 곧바로 조회하고, "아직 진행 중"
     * 을 보고도 "서명이 완료되었습니다. 다음 단계를 안내하겠습니다" 를 이어 썼다.
     */
    @Test
    void 카드를_띄우면_도구_없이_말로_끝낸다() {
        List<List<LlmMessage>> prompts = new java.util.ArrayList<>();
        List<Integer> toolCounts = new java.util.ArrayList<>();
        when(llmClient.complete(anyList(), anyList()))
                .thenAnswer(invocation -> {
                    prompts.add(List.copyOf(invocation.getArgument(0)));
                    toolCounts.add(((List<?>) invocation.getArgument(1)).size());
                    return prompts.size() == 1
                            ? new LlmCompletion(null, List.of(new ToolCall(
                                    "call-1", "request_chain_order", "{\"tradeId\":1,\"summary\":\"소재 온체인 기록\"}")))
                            : new LlmCompletion("온체인 기록 서명 카드를 띄웠습니다. 카드에서 서명해 주세요.", List.of());
                });

        ConversationDetailResponse detail = conversationService.start(brandId, "기록 남겨줘");

        assertThat(toolCounts).hasSize(2);
        // 둘째 호출에는 도구가 없다 — 조회로 새지 않는다.
        assertThat(toolCounts.get(1)).isZero();
        assertThat(prompts.get(1))
                .filteredOn(message -> message.role() == LlmRole.SYSTEM)
                .anyMatch(message -> message.content().contains("방금 확인 카드를 띄웠다"));
        assertThat(detail.messages().getLast().content()).isEqualTo("온체인 기록 서명 카드를 띄웠습니다. 카드에서 서명해 주세요.");
    }

    @Test
    void 없는_이름의_tool_은_카드를_만들지_않고_있는_이름에서_고르라고_한다() {
        // 모델이 이름을 지어내 부르는 것만으로 담당자에게 누를 카드가 생기면 안 된다.
        // 카드는 눌리면 도메인이 도는 자리다.
        //
        // 단계를 옮기라고 안내하지도 않는다. 옮긴다고 생기는 이름이 아니라, 그렇게 안내하면
        // 모델이 옮긴 뒤 같은 이름을 다시 부른다.
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion(
                        null,
                        List.of(new ToolCall(
                                "call-1",
                                "confirm_everything",
                                "{\"tradeId\":1,\"summary\":\"수령 확정\"}"))))
                .thenReturn(new LlmCompletion("그 기능은 없습니다.", List.of()));

        ConversationDetailResponse detail = conversationService.start(brandId, "수령 확정해줘");

        assertThat(lastToolResult()).contains("UNKNOWN_TOOL").doesNotContain("set_phase");
        assertThat(detail.actions()).isEmpty();
        assertThat(pendingActions.count()).isZero();
    }

    @Test
    void 이탈한_거래는_대화_안에서_정리할_수_있다() {
        // 결제창을 닫고 온 거래는 CREATED 로 남아 판매 수량을 묶는다. 대화에 정리 수단이
        // 없으면 담당자가 거래 목록 화면으로 나가야 하고, 그건 이 기능이 없애려던 이동이다.
        answerWith("네");
        conversationService.start(brandId, "소재 사고 싶어요");
        Long conversationId = conversations.findAll().getFirst().getId();

        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion(
                        null,
                        List.of(new ToolCall(
                                "call-1",
                                "cancel_trade",
                                "{\"tradeId\":1,\"reason\":\"다른 소재로 결정\",\"summary\":\"거래 취소\"}"))))
                .thenReturn(new LlmCompletion("취소할까요?", List.of()));

        ConversationDetailResponse detail =
                conversationService.send(brandId, conversationId, "아까 그 거래 취소해줘");

        // 취소도 되돌릴 수 없는 작업이라 확인 카드를 거친다. 모델이 불렀다고 바로 돌면 안 된다.
        assertThat(detail.actions()).hasSize(1);
        assertThat(detail.actions().getFirst().actionType()).isEqualTo(PendingActionType.CONFIRM);
        assertThat(lastToolResult()).contains("AWAITING_USER_CONFIRMATION");
    }

    /**
     * 상한에 걸려도 <b>모아 둔 답을 버리지 않는다.</b>
     *
     * <p>회차마다 "글로 답하거나 / tool 을 부르거나" 둘 중 하나라서, 상한을 tool 에 다 쓰면
     * 결론을 쓸 자리가 없다. 그때 고정 문구를 내보내면 담당자는 자기가 설명을 못 한 줄
     * 알게 된다 — 실제로 조회는 다 끝나 있었는데도 그랬다.
     */
    @Test
    void 상한에_걸리면_tool_없이_한_번_더_불러_말로_끝맺는다() {
        // 모델이 결론을 못 내고 같은 tool 을 계속 부르는 상황. 상한(test 설정 3)에 걸린다.
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion(null, List.of(new ToolCall("c", "set_phase", "{\"phase\":\"INTAKE\"}"))));
        // tool 목록을 비워 부르면 모델은 글로 답할 수밖에 없다. 더 구체적인 스텁이 이긴다.
        when(llmClient.complete(anyList(), argThat(List::isEmpty)))
                .thenReturn(new LlmCompletion("실크 판매 글이 아직 없습니다. 소재 종류를 비우고 찾아볼까요?", List.of()));

        ConversationDetailResponse detail = conversationService.start(brandId, "계속 돌아라");

        // 상한 3회 + 마무리 1회.
        verify(llmClient, times(3)).complete(anyList(), argThat(tools -> !tools.isEmpty()));
        verify(llmClient, times(1)).complete(anyList(), argThat(List::isEmpty));
        assertThat(detail.messages().getLast().role()).isEqualTo(AgentMessageRole.ASSISTANT);
        assertThat(detail.messages().getLast().content()).isEqualTo("실크 판매 글이 아직 없습니다. 소재 종류를 비우고 찾아볼까요?");
    }

    /** 마무리 호출까지 실패하면 그때는 고정 문구다. 말없이 끝내면 화면이 비어 버린다. */
    @Test
    void 마무리_호출이_실패하면_되묻는_문구로_내려간다() {
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion(null, List.of(new ToolCall("c", "set_phase", "{\"phase\":\"INTAKE\"}"))));
        when(llmClient.complete(anyList(), argThat(List::isEmpty)))
                .thenThrow(new BusinessException(AgentErrorCode.AGENT_LLM_UNAVAILABLE));

        ConversationDetailResponse detail = conversationService.start(brandId, "계속 돌아라");

        assertThat(detail.messages().getLast().role()).isEqualTo(AgentMessageRole.ASSISTANT);
        assertThat(detail.messages().getLast().content()).contains("조금 더 구체적으로");
    }

    @Test
    void 남의_대화는_열람할_수_없다() {
        answerWith("네");
        ConversationDetailResponse mine = conversationService.start(brandId, "안녕");

        Long otherBrandId = users.save(User.register(
                        "agent-other@test.com", "{noop}x", "other", "010-1111-1111", UserRole.BRAND))
                .getId();

        assertThatThrownBy(() -> conversationService.detail(otherBrandId, mine.id()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("대화를 찾을 수 없습니다");
    }

    @Test
    void 브랜드가_아니면_거절한다() {
        Long supplierId = users.save(User.register(
                        "agent-supplier@test.com", "{noop}x", "supplier", "010-2222-2222", UserRole.SUPPLIER))
                .getId();

        assertThatThrownBy(() -> conversationService.start(supplierId, "안녕"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("브랜드 계정만");
    }

    @Test
    void 종료된_대화에는_메시지를_보낼_수_없다() {
        answerWith("네");
        ConversationDetailResponse detail = conversationService.start(brandId, "안녕");
        conversationService.abandon(brandId, detail.id());

        assertThatThrownBy(() -> conversationService.send(brandId, detail.id(), "더 할 말 있어"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 종료된 대화");
    }

    /**
     * 담당자의 말 없이 이어 말할 때(결제 복귀·카드 승인 뒤) 요청이 <b>모델 자신의 말로 끝나지 않게</b>
     * 화면 알림을 붙인다. 이력에는 남기지 않는다.
     *
     * <p>요청이 모델의 말로 끝나면 API 는 그것을 이어 쓸 문장으로 받는다. 이미 끝난 문장이라 모델은
     * 아무것도 덧붙이지 않았고, 승인 뒤 매 턴이 빈 답으로 끝나 서버 문장("결제가 완료되었습니다.
     * (거래 30번)")이 대신 나갔다.
     */
    @Test
    @SuppressWarnings("unchecked")
    void 담당자_말_없이_이어_말할_때는_화면_알림을_붙이고_저장하지_않는다() {
        answerWith("어떤 지갑을 만드실 건가요?");
        ConversationDetailResponse detail = conversationService.start(brandId, "지갑 만들고 싶어");

        answerWith("결제가 끝났습니다. 이제 주문 기록에 서명해 주세요.");
        conversationService.reportEvent(brandId, detail.id(), "결제가 완료되었습니다. (거래 30번)");

        org.mockito.ArgumentCaptor<List<LlmMessage>> sent = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(llmClient, org.mockito.Mockito.atLeastOnce()).complete(sent.capture(), anyList());
        LlmMessage last = sent.getAllValues().getLast().getLast();
        assertThat(last.role()).isEqualTo(LlmRole.USER);
        assertThat(last.content()).startsWith("[화면 알림");
        assertThat(messages.findAll())
                .as("담당자가 쓴 말이 아니므로 대화 기록에 남기지 않는다")
                .noneMatch(message -> message.getContent() != null && message.getContent().startsWith("[화면 알림"));
    }

    /** 담당자가 말하면 붙이지 않는다 — 담당자의 말이 마지막이다. */
    @Test
    @SuppressWarnings("unchecked")
    void 담당자가_말하면_화면_알림을_붙이지_않는다() {
        answerWith("어떤 지갑을 만드실 건가요?");
        conversationService.start(brandId, "지갑 만들고 싶어");

        org.mockito.ArgumentCaptor<List<LlmMessage>> sent = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(llmClient, org.mockito.Mockito.atLeastOnce()).complete(sent.capture(), anyList());
        LlmMessage last = sent.getAllValues().getLast().getLast();
        assertThat(last.role()).isEqualTo(LlmRole.USER);
        assertThat(last.content()).isEqualTo("지갑 만들고 싶어");
    }

    private void answerWith(String content) {
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion(content, List.of()));
    }

    /** 이력의 마지막 tool 결과. 화면 DTO 는 tool 줄을 걸러내므로 저장소에서 직접 읽는다. */
    private String lastToolResult() {
        return messages.findAll().stream()
                .filter(message -> message.getRole() == AgentMessageRole.TOOL)
                .reduce((first, second) -> second)
                .orElseThrow()
                .getToolPayloadJson();
    }
}
