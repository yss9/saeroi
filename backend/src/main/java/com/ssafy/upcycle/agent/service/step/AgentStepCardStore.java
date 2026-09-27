package com.ssafy.upcycle.agent.service.step;

import com.ssafy.upcycle.agent.config.AgentProperties;
import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.entity.AgentMessage;
import com.ssafy.upcycle.agent.entity.AgentPendingAction;
import com.ssafy.upcycle.agent.entity.PendingActionStatus;
import com.ssafy.upcycle.agent.entity.PendingActionType;
import com.ssafy.upcycle.agent.repository.AgentPendingActionRepository;
import com.ssafy.upcycle.agent.service.ConversationStore;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자동 카드의 <b>DB 작업만</b> 맡는 짧은 트랜잭션들.
 *
 * <h2>왜 트랜잭션을 여기로 내렸나 — 사전 검사를 쓰기 트랜잭션 안에서 돌리면 안 된다</h2>
 *
 * <p>카드를 띄우기 전에 도구가 스스로 보는 검사({@code refuseBeforeProposal} ·
 * {@code stalenessReason})는 도메인 서비스를 부른다. 그 서비스들이
 * {@code @Transactional} 인데, <b>거기서 던진 예외를 밖에서 잡아도 Spring 은 바깥
 * 트랜잭션을 rollback-only 로 표시한다.</b> 그러면 같은 트랜잭션에서 한 일이 전부 커밋되지
 * 않고, 커밋 시점에 {@code UnexpectedRollbackException} 이 난다.
 *
 * <p>이 저장소는 그 함정에 이미 한 번 데었다 — 없는 거래 id 로 카드가 떠 있을 때 승인 전체가
 * 그렇게 깨졌고, 그래서 {@code ConfirmReceiptTool} 은 서비스 대신 리포지토리를 직접 읽는다.
 * 자동 카드는 <b>20초마다</b> 도는 경로라 같은 일이 나면 조용히 한 번 깨지는 것이 아니라
 * 계속 깨진다.
 *
 * <p>그래서 {@link BrandStepAutoProposer} 는 트랜잭션 없이 "무엇을 띄울지" 만 정하고, 쓰기는
 * 여기 있는 짧은 트랜잭션들이 맡는다. 검사가 흔들려도 그 트랜잭션은 검사를 부른 서비스 자신의
 * 것이라, 닫히고 나면 이쪽에 남는 것이 없다. {@code PendingActionService} 가
 * {@code AgentActionStore} 를 두는 것과 같은 이유다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentStepCardStore {

    private final AgentPendingActionRepository pendingActions;
    private final ConversationStore conversations;
    private final AgentProperties properties;
    private final Clock clock;

    /** 지금 떠 있는 카드. 같은 카드를 두 번 띄우지 않으려고 본다. */
    @Transactional(readOnly = true)
    public List<AgentPendingAction> open(Long conversationId) {
        return pendingActions.findByConversationIdAndStatus(
                conversationId, PendingActionStatus.PENDING);
    }

    /**
     * 기한이 지난 카드를 닫는다.
     *
     * <p>기한은 이미 모든 쓰기 경로가 존중한다 — 승인하려 하면 {@code AgentActionStore} 가
     * 만료로 돌려세운다. 그런데 <b>읽기 경로는 그 말을 하지 않는다.</b> 상태가 아직
     * {@code PENDING} 이라 화면은 멀쩡한 카드로 그리고, 담당자는 눌러 본 뒤에야 기한이
     * 지났다는 것을 안다. 실제로 하루 전 인계 카드가 그대로 떠 있었다.
     *
     * @return 닫은 카드 수
     */
    @Transactional
    public int expireTimedOut(Long conversationId) {
        LocalDateTime now = LocalDateTime.now(clock);
        int expired = 0;
        for (AgentPendingAction action : open(conversationId)) {
            if (action.isExpiredAt(now)) {
                action.expire();
                expired++;
                log.info(
                        "Agent card expired on poll: id={} tool={}",
                        action.getId(),
                        action.getToolName());
            }
        }
        return expired;
    }

    /**
     * 카드 하나를 띄운다. <b>안내 한 줄과 함께.</b>
     *
     * <p>카드는 대화의 한 줄에 붙는다. 그 줄이 담당자가 읽을 안내다 — 카드만 불쑥 뜨면 왜
     * 떴는지 알 수 없다.
     */
    @Transactional
    public void propose(
            AgentConversation conversation,
            String notice,
            PendingActionType actionType,
            String toolName,
            String summary,
            String payloadJson,
            int signatureSteps) {

        AgentMessage anchor = conversations.appendAssistant(conversation, notice);
        AgentPendingAction card = pendingActions.save(AgentPendingAction.propose(
                conversation.getId(),
                anchor.getId(),
                actionType,
                toolName,
                summary,
                payloadJson,
                signatureSteps,
                LocalDateTime.now(clock).plus(properties.actionTtl())));

        log.info(
                "Agent step auto-proposed: id={} conversationId={} tool={}",
                card.getId(),
                conversation.getId(),
                toolName);
    }
}
