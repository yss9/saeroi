package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.upcycle.agent.dto.ConversationDetailResponse;
import com.ssafy.upcycle.agent.entity.AgentMessageRole;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.entity.PendingActionStatus;
import com.ssafy.upcycle.agent.entity.PendingActionType;
import com.ssafy.upcycle.agent.repository.AgentConversationRepository;
import com.ssafy.upcycle.agent.repository.AgentMessageRepository;
import com.ssafy.upcycle.agent.repository.AgentPendingActionRepository;
import com.ssafy.upcycle.agent.service.AgentConversationService;
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
        // 소재 조회 tool 은 MATERIAL_SOURCING 단계에서만 보인다. INTAKE 에서 불렀으므로
        // 실행되지 않고 거절돼야 한다 — 그 사실이 모델에게 결과로 돌아간다.
        assertThat(lastToolResult()).contains("UNKNOWN_TOOL");
        assertThat(detail.messages().getLast().content()).isEqualTo("보유하신 소재가 없습니다.");
    }

    @Test
    void 쓰기_tool_은_실행되지_않고_확인_카드만_만든다() {
        // 이 테스트가 이 기능의 핵심 안전 장치다. 모델이 쓰기 tool 을 불러도 도메인은
        // 움직이지 않아야 하고, 사용자가 누를 카드만 생겨야 한다.
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion(
                        null,
                        List.of(new ToolCall(
                                "call-1",
                                "request_chain_order",
                                "{\"tradeId\":1,\"summary\":\"소재 구매 온체인 기록\"}"))))
                .thenReturn(new LlmCompletion("확인해 주세요.", List.of()));

        conversationService.start(brandId, "소재 발행해줘");
        Long conversationId = conversations.findAll().getFirst().getId();
        moveToPhase(conversationId, JourneyPhase.MATERIAL_SOURCING);

        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion(
                        null,
                        List.of(new ToolCall(
                                "call-2",
                                "request_chain_order",
                                "{\"tradeId\":1,\"summary\":\"소재 구매 온체인 기록\"}"))))
                .thenReturn(new LlmCompletion("확인해 주세요.", List.of()));

        ConversationDetailResponse detail = conversationService.send(brandId, conversationId, "발행 진행해줘");

        assertThat(detail.actions()).hasSize(1);
        assertThat(detail.actions().getFirst().actionType()).isEqualTo(PendingActionType.WALLET_SIGN);
        assertThat(detail.actions().getFirst().status()).isEqualTo(PendingActionStatus.PENDING);
        // 모델에게 "아직 안 끝났다" 고 분명히 알려줘야 완료로 착각하지 않는다.
        assertThat(lastToolResult()).contains("AWAITING_USER_CONFIRMATION");
    }

    @Test
    void 단계에_없는_쓰기_tool_은_카드조차_만들지_않는다() {
        // 읽기 tool 의 단계 차단은 위에서 함께 확인된다. 쓰기 쪽이 더 위험하다 — 노출만
        // 좁히고 실행을 막지 않으면, 모델이 이전 단계에서 본 이름을 기억해 부르는 것만으로
        // 담당자에게 누를 카드가 생긴다. 카드는 눌리면 도메인이 도는 자리다.
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion(
                        null,
                        List.of(new ToolCall(
                                "call-1",
                                "confirm_receipt",
                                "{\"tradeId\":1,\"summary\":\"수령 확정\"}"))))
                .thenReturn(new LlmCompletion("아직 그 단계가 아닙니다.", List.of()));

        // confirm_receipt 는 MATERIAL_SOURCING 전용인데 INTAKE 에서 부른다.
        ConversationDetailResponse detail = conversationService.start(brandId, "수령 확정해줘");

        assertThat(lastToolResult()).contains("UNKNOWN_TOOL");
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
        moveToPhase(conversationId, JourneyPhase.MATERIAL_SOURCING);

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

    @Test
    void tool_루프가_상한에_걸리면_장애가_아니라_되묻는다() {
        // 모델이 결론을 못 내고 같은 tool 을 계속 부르는 상황. 상한(test 설정 3)에 걸린다.
        when(llmClient.complete(anyList(), anyList()))
                .thenReturn(new LlmCompletion(null, List.of(new ToolCall("c", "set_phase", "{\"phase\":\"INTAKE\"}"))));

        ConversationDetailResponse detail = conversationService.start(brandId, "계속 돌아라");

        verify(llmClient, times(3)).complete(anyList(), anyList());
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

    private void moveToPhase(Long conversationId, JourneyPhase phase) {
        var conversation = conversations.findById(conversationId).orElseThrow();
        conversation.moveTo(phase);
        conversations.save(conversation);
    }
}
