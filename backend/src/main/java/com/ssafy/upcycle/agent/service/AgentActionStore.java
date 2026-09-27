package com.ssafy.upcycle.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.upcycle.agent.AgentErrorCode;
import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.entity.AgentPendingAction;
import com.ssafy.upcycle.agent.entity.PendingActionType;
import com.ssafy.upcycle.agent.project.AgentProjects;
import com.ssafy.upcycle.agent.repository.AgentPendingActionRepository;
import com.ssafy.upcycle.agent.tool.AgentToolArgumentException;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.common.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 승인된 카드의 <b>DB 작업만</b> 맡는 짧은 트랜잭션들.
 *
 * <p>{@link AgentTurnStore} 와 같은 이유로 갈라 두었다. 카드를 승인하면 도메인이 돌고
 * 이어서 모델이 다음 말을 하는데, 그 LLM 호출이 20~30초 걸린다. 한 트랜잭션으로 묶으면
 * 승인 한 번이 DB 커넥션 하나를 그동안 쥐고 있어, 기본 풀 10개가 동시 10명에서 고갈된다.
 *
 * <p><b>거절을 기록하는 트랜잭션이 따로인 이유.</b> 도메인이 던진 예외가 트랜잭션 경계를
 * 지나면 Spring 이 그 트랜잭션에 rollback-only 를 건다. 같은 트랜잭션 안에서 예외를 잡아
 * "왜 안 됐는지" 를 적어 봐야 커밋할 때 함께 지워지고, 담당자는 이유 없는 오류만 보게 된다.
 * 실패한 트랜잭션은 그대로 접고, 기록은 새 트랜잭션에서 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentActionStore {

    private final AgentPendingActionRepository pendingActions;
    private final AgentConversationService conversationService;
    private final AgentToolExecutor executor;
    private final AgentProjects projects;
    private final ConversationStore store;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 승인 처리 결과. 대화를 이어갈지는 호출자가 트랜잭션 <b>밖에서</b> 판단한다.
     *
     * @param summary 무슨 일이 일어났는지 한 줄. <b>이력에 넣지 않는다</b> — 모델에게는 이번
     *     턴의 상태 블록으로 전해지고, 담당자에게 보일 문장은 모델이 쓴다. 예전에는 이것을
     *     ASSISTANT 로 남겼는데, 곧이어 모델이 같은 말을 다시 해서 "수령이 블록체인에
     *     기록되고 확정됐습니다" 가 세 건에 네 번 떴다. 모델이 말하지 못하면 이 문장이
     *     그대로 화면에 나간다
     */
    public record Outcome(Long conversationId, boolean resumeConversation, String summary) {}

    /**
     * 승인된 카드를 실행한다. 실패하면 {@link ActionFailed} 로 나간다.
     *
     * <p>예외로 나가는 것이 의도다 — 이 트랜잭션은 롤백되어야 하고, 기록은 호출자가 새
     * 트랜잭션에서 남긴다.
     */
    @Transactional
    public Outcome apply(Long userId, Long actionId, String resultJson) {
        AgentPendingAction action = pendingActions
                .findById(actionId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_ACTION_NOT_FOUND));

        // 액션 id 만으로는 소유자를 알 수 없다. 대화를 통해 확인한다 — require() 가
        // 소유자 조건을 쿼리에 박아 두고 있어 남의 액션이면 여기서 not-found 가 난다.
        AgentConversation conversation =
                conversationService.require(userId, action.getConversationId());

        LocalDateTime now = LocalDateTime.now(clock);
        if (!action.isPending() || action.isExpiredAt(now)) {
            action.expire();
            throw new BusinessException(AgentErrorCode.AGENT_ACTION_NOT_PENDING);
        }

        JsonNode arguments = mergeArguments(action, resultJson);

        // 카드를 만든 뒤에 담당자가 화면에서 먼저 처리했을 수 있다. 그대로 실행하면 도메인이
        // 거절하고, 담당자는 자기가 승인한 작업이 오류로 끝나는 것을 본다. 이미 끝난 일이면
        // 실패가 아니라 "이미 됐다" 로 닫는다.
        Optional<String> stale = executor.stalenessReason(conversation, userId, action, arguments);
        if (stale.isPresent()) {
            action.markStale();
            store.appendAssistant(conversation, stale.get());
            log.info("Agent action stale on approval: id={} tool={}", actionId, action.getToolName());
            return new Outcome(conversation.getId(), false, stale.get());
        }

        AgentToolResult result = runOrFail(conversation, userId, action, arguments);

        action.complete(result.payloadJson(), now);
        log.info("Agent action completed: id={} tool={}", actionId, action.getToolName());

        // 이 카드가 프로젝트를 다음 단계로 들였으면 대화 단계도 따라간다. 진행 막대와 단계
        // 안내가 이 값을 읽는데, 모델이 set_phase 를 잊으면 실제 진행보다 뒤처진다.
        projects.claimedBy(action.getToolName(), arguments, result.payloadJson())
                .furthestStage()
                .ifPresent(conversation::advanceTo);

        // **이력에 넣지 않는다.** 여기 넣으면 담당자 화면에 서버 문장이 뜨고, 이어지는
        // 턴에서 모델이 같은 말을 한 번 더 한다. 결과는 이번 턴의 상태 블록으로만 전하고,
        // 화면에 보일 문장은 모델이 한 번만 쓴다.
        //
        // **결제 카드는 여기서 모델을 부르지 않는다.** 승인은 결제창을 연 것일 뿐 결제가 아니다. 예전에는 여기서
        // 모델이 돌아, 결제창이 뜨기도 전에 "✓ 결제 완료, 판매자 발송 대기 중" 을 썼다 — 결제는 15초 뒤였고,
        // 담당자는 카드를 누르고 결제창이 뜨기까지 그 9초를 기다렸다. 결제를 마치거나 닫고 돌아오면 복귀 화면이
        // 무슨 일이 있었는지 알리고 모델이 그때 말한다({@code AgentChatService.reportEvent}).
        return new Outcome(conversation.getId(), resumesAfter(action.getActionType()), result.userVisibleSummary());
    }

    /**
     * 실패를 담당자가 읽을 문장으로 바꾼다.
     *
     * <p><b>카드를 띄울 때와 같은 폭으로 잡는다.</b> 제안 시점에는 {@link AgentToolExecutor}
     * 가 인자 오류·도메인 거절·그 밖의 실패를 모두 문장으로 돌려주는데, 승인 시점에서 도메인
     * 거절만 잡으면 나머지는 500 이 된다. 같은 작업이 어느 문으로 들어왔는지에 따라 다르게
     * 끝날 이유가 없다.
     */
    private AgentToolResult runOrFail(
            AgentConversation conversation,
            Long userId,
            AgentPendingAction action,
            JsonNode arguments) {
        try {
            return executor.runApproved(conversation, userId, action, arguments);
        } catch (BusinessException domainRejection) {
            log.info(
                    "Agent action rejected by domain: id={} code={}",
                    action.getId(),
                    domainRejection.getErrorCode().code());
            throw new ActionFailed(
                    conversation.getId(),
                    "요청을 처리하지 못했습니다: " + domainRejection.getErrorCode().message());
        } catch (AgentToolArgumentException badArguments) {
            // 모델이 채운 인자가 승인 뒤에야 걸린 경우다. 담당자 잘못이 아니므로 그대로 알린다.
            log.info(
                    "Agent action had bad arguments: id={} tool={}",
                    action.getId(),
                    action.getToolName());
            throw new ActionFailed(
                    conversation.getId(), "요청을 처리하지 못했습니다: " + badArguments.getMessage());
        } catch (RuntimeException unexpected) {
            // 예상 못 한 실패다. 담당자에게는 일반적인 문장을, 로그에는 원인을 남긴다 —
            // 카드를 PENDING 으로 남겨 두면 담당자는 눌러도 계속 같은 오류만 본다.
            log.warn(
                    "Agent action failed unexpectedly: id={} tool={}",
                    action.getId(),
                    action.getToolName(),
                    unexpected);
            throw new ActionFailed(
                    conversation.getId(), "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    /**
     * 실패를 기록한다. <b>반드시 실패한 트랜잭션 밖에서</b> 불러야 한다.
     *
     * <p>이미 롤백이 예정된 트랜잭션 안에서 부르면 이 쓰기도 함께 지워진다. 그것이 이
     * 클래스를 만든 이유다.
     */
    @Transactional
    public void recordFailure(Long userId, Long actionId) {
        AgentPendingAction action = pendingActions
                .findById(actionId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_ACTION_NOT_FOUND));
        conversationService.require(userId, action.getConversationId());

        if (action.isPending()) {
            action.reject(LocalDateTime.now(clock));
        }
        // **이유는 여기서 대화에 넣지 않는다.** 이어지는 턴에서 모델이 무엇이 막혔는지와 다음
        // 할 일을 한 번 전하고, 모델이 말하지 못하면 그때 서버 문장이 나간다
        // ({@code PendingActionService.resumeQuietly}). 여기서도 넣으면 담당자는 같은 실패를
        // 서버 문장과 모델 문장으로 두 번 읽는다.
    }

    /**
     * 담당자에게 보일 안내 한 줄을 대화에 남긴다.
     *
     * <p>승인 뒤 모델이 이어 말하지 못했을 때 쓴다({@code PendingActionService.resumeQuietly}).
     * 조용히 넘기면 담당자는 승인 뒤 아무 반응도 없는 화면을 보게 되고, 그것도 실패처럼
     * 읽힌다.
     */
    @Transactional
    public void appendNotice(Long userId, Long conversationId, String notice) {
        store.appendAssistant(conversationService.require(userId, conversationId), notice);
    }

    /** 승인 뒤 곧바로 모델이 이어 말하는가. 결제 카드는 복귀 화면이 결과를 알린 뒤에 말한다. */
    public static boolean resumesAfter(PendingActionType type) {
        return type != PendingActionType.PAYMENT;
    }

    /** 담당자가 취소한 카드. 이어가기는 트랜잭션 밖에서 하므로 필요한 값만 들고 나간다. */
    public record Rejection(Long conversationId, String summary) {}

    /**
     * 담당자가 카드를 거절했다.
     *
     * <p><b>대화에 문장을 남기지 않는다.</b> 예전에는 서버가 "담당자가 이 작업을 취소했습니다." 를 먼저 쓰고
     * 모델을 불렀다. 담당자는 서버 문장과 모델의 말을 둘 다 봤고, 서버 문장에는 무엇을 취소했는지도 없었다.
     * 이제 무엇을 취소했는지를 돌려주고, 모델이 그것을 이어 말한다({@code PendingActionService.reject}).
     */
    @Transactional
    public Rejection recordUserRejection(Long userId, Long actionId) {
        AgentPendingAction action = pendingActions
                .findById(actionId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_ACTION_NOT_FOUND));
        AgentConversation conversation =
                conversationService.require(userId, action.getConversationId());

        LocalDateTime now = LocalDateTime.now(clock);
        if (!action.isPending() || action.isExpiredAt(now)) {
            action.expire();
            throw new BusinessException(AgentErrorCode.AGENT_ACTION_NOT_PENDING);
        }
        action.reject(now);
        return new Rejection(conversation.getId(), action.getSummary());
    }

    /**
     * 모델이 제안한 인자와 사용자가 만든 결과를 합친다.
     *
     * <p>사용자 결과를 {@code _userResult} 라는 별도 키에 넣고 인자 위에 덮어쓰지 않는다.
     * 덮어쓰게 두면 화면이 보낸 값이 사용자가 카드에서 승인한 내용을 조용히 바꿀 수 있다 —
     * 카드에는 100kg 이라 적혀 있는데 실행은 200kg 으로 되는 상황을 만들지 않는다.
     */
    private JsonNode mergeArguments(AgentPendingAction action, String resultJson) {
        try {
            JsonNode base = objectMapper.readTree(action.getPayloadJson());
            if (!(base instanceof ObjectNode merged)) {
                throw new IllegalStateException("Pending action payload must be a JSON object");
            }
            if (StringUtils.hasText(resultJson)) {
                merged.set("_userResult", objectMapper.readTree(resultJson));
            }
            return merged;
        } catch (IllegalStateException invalid) {
            throw invalid;
        } catch (Exception malformed) {
            throw new BusinessException(AgentErrorCode.AGENT_ACTION_NOT_PENDING);
        }
    }

    /**
     * 승인된 작업이 실패했다는 신호.
     *
     * <p>{@link BusinessException} 을 쓰지 않는다 — 그건 "요청이 잘못됐다" 는 뜻이라 컨트롤러
     * 단에서 오류 응답이 되는데, 여기서는 담당자에게 정상 응답으로 이유를 보여주려는 것이다.
     */
    @Getter
    public static class ActionFailed extends RuntimeException {

        private final transient Long conversationId;

        ActionFailed(Long conversationId, String reason) {
            super(reason);
            this.conversationId = conversationId;
        }
    }
}
