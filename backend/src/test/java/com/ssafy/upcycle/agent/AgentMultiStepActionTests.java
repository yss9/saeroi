package com.ssafy.upcycle.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.entity.AgentMessage;
import com.ssafy.upcycle.agent.entity.AgentPendingAction;
import com.ssafy.upcycle.agent.entity.ConversationStatus;
import com.ssafy.upcycle.agent.entity.PendingActionStatus;
import com.ssafy.upcycle.agent.entity.PendingActionType;
import com.ssafy.upcycle.agent.repository.AgentConversationRepository;
import com.ssafy.upcycle.agent.repository.AgentMessageRepository;
import com.ssafy.upcycle.agent.repository.AgentPendingActionRepository;
import com.ssafy.upcycle.agent.service.PendingActionService;
import com.ssafy.upcycle.agent.service.llm.LlmClient;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 다단계 서명 카드.
 *
 * <p>생산 확정처럼 컨트랙트가 순서를 강제하는 연속 서명을 카드 하나로 묶는 경로다. 여기서
 * 지켜야 할 것은 셋이다 — 중간 단계가 모델을 부르지 않을 것(크레딧), 마지막 단계만 완료로
 * 갈 것, 그리고 끊긴 카드를 이어갈 수 있을 것.
 */
@SpringBootTest
class AgentMultiStepActionTests {

    @Autowired private PendingActionService pendingActionService;
    @Autowired private AgentConversationRepository conversations;
    @Autowired private AgentMessageRepository messages;
    @Autowired private AgentPendingActionRepository pendingActions;
    @Autowired private UserRepository users;
    @Autowired private Clock clock;

    @MockitoBean private LlmClient llmClient;

    private Long brandId;
    private AgentPendingAction action;

    @BeforeEach
    void setUp() {
        brandId = users.save(User.register(
                        "multistep-brand@test.com", "{noop}x", "brand", "010-0000-0000", UserRole.BRAND))
                .getId();

        AgentConversation conversation =
                conversations.save(AgentConversation.start(brandId, "생산 확정"));
        AgentMessage message = messages.save(AgentMessage.assistant(
                conversation.getId(), 0, "서명이 필요합니다.", LocalDateTime.now(clock)));

        // 4단계짜리 카드. 생산 확정이 이 모양이다.
        action = pendingActions.save(AgentPendingAction.propose(
                conversation.getId(),
                message.getId(),
                PendingActionType.WALLET_SIGN,
                "request_chain_order",
                "생산 확정 — 서명 4회",
                "{\"tradeId\":1,\"summary\":\"생산 확정\"}",
                4,
                LocalDateTime.now(clock).plusHours(1)));
    }

    @AfterEach
    void clean() {
        pendingActions.deleteAll();
        messages.deleteAll();
        conversations.deleteAll();
        users.deleteAll();
    }

    @Test
    void 중간_단계는_모델을_부르지_않는다() {
        // 단계마다 LLM 을 부르면 크레딧이 단계 수만큼 든다. 토큰당 차감이라 그대로 비용이다.
        pendingActionService.recordStep(brandId, action.getId(), "{\"step\":\"CREATE_BATCH\",\"txHash\":\"0xa\"}");

        verify(llmClient, never()).complete(anyList(), anyList());
        AgentPendingAction reloaded = pendingActions.findById(action.getId()).orElseThrow();
        assertThat(reloaded.getStepDone()).isEqualTo(1);
        assertThat(reloaded.getStatus()).isEqualTo(PendingActionStatus.PENDING);
    }

    @Test
    void 단계_결과가_순서대로_쌓인다() {
        pendingActionService.recordStep(brandId, action.getId(), "{\"step\":\"CREATE_BATCH\"}");
        pendingActionService.recordStep(brandId, action.getId(), "{\"step\":\"SUBMIT_PLAN\"}");

        AgentPendingAction reloaded = pendingActions.findById(action.getId()).orElseThrow();
        assertThat(reloaded.getStepDone()).isEqualTo(2);
        assertThat(reloaded.getResultJson()).contains("CREATE_BATCH").contains("SUBMIT_PLAN");
        // 아직 진행 중이다. 카드가 닫히면 담당자가 남은 서명을 할 수 없다.
        assertThat(reloaded.getStatus()).isEqualTo(PendingActionStatus.PENDING);
    }

    @Test
    void 마지막_단계는_step_으로_처리할_수_없다() {
        pendingActionService.recordStep(brandId, action.getId(), "{\"step\":\"1\"}");
        pendingActionService.recordStep(brandId, action.getId(), "{\"step\":\"2\"}");
        pendingActionService.recordStep(brandId, action.getId(), "{\"step\":\"3\"}");

        // 4번째는 complete 로 가야 한다. 여기서 처리하면 도메인 호출이 빠진 채 끝난다.
        assertThatThrownBy(() ->
                        pendingActionService.recordStep(brandId, action.getId(), "{\"step\":\"4\"}"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 남의_카드는_단계를_보고할_수_없다() {
        Long otherBrandId = users.save(User.register(
                        "multistep-other@test.com", "{noop}x", "other", "010-1111-1111", UserRole.BRAND))
                .getId();

        assertThatThrownBy(() -> pendingActionService.recordStep(otherBrandId, action.getId(), "{}"))
                .isInstanceOf(BusinessException.class);
        assertThat(pendingActions.findById(action.getId()).orElseThrow().getStepDone()).isZero();
    }

    @Test
    void 담당자당_활성_대화는_하나다() {
        // setUp 이 이미 하나를 만들었다. 같은 담당자의 두 번째 활성 대화는 만들지 않는다.
        assertThat(conversations.findByBrandUserIdAndStatus(brandId, ConversationStatus.ACTIVE))
                .isPresent();
    }
}
