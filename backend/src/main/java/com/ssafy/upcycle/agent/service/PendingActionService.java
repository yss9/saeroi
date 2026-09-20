package com.ssafy.upcycle.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.upcycle.agent.AgentErrorCode;
import com.ssafy.upcycle.agent.dto.ConversationDetailResponse;
import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.entity.AgentPendingAction;
import com.ssafy.upcycle.agent.repository.AgentPendingActionRepository;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.common.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 사용자가 확인 카드를 눌렀을 때의 경로. <b>쓰기 tool 이 실제로 도는 유일한 지점이다.</b>
 *
 * <p>모델은 이 클래스를 부를 수 없다. 오직 인증된 HTTP 요청만 여기 닿는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingActionService {

    private final AgentPendingActionRepository pendingActions;
    private final AgentConversationService conversationService;
    private final AgentToolExecutor executor;
    private final ConversationStore store;
    private final AgentChatService chatService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 승인된 액션을 실행하고 대화를 이어간다.
     *
     * @param resultJson 화면이 만든 결과(txHash, pgToken …). 없을 수도 있다(단순 확인)
     */
    @Transactional
    public ConversationDetailResponse complete(Long userId, Long actionId, String resultJson) {
        AgentConversation conversation = null;
        AgentPendingAction action = pendingActions
                .findById(actionId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_ACTION_NOT_FOUND));

        // 액션 id 만으로는 소유자를 알 수 없다. 대화를 통해 확인한다 — require() 가
        // 소유자 조건을 쿼리에 박아 두고 있어 남의 액션이면 여기서 not-found 가 난다.
        conversation = conversationService.require(userId, action.getConversationId());

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
            return conversationService.detail(userId, conversation.getId());
        }

        AgentToolResult result;
        try {
            result = executor.runApproved(conversation, userId, action, arguments);
        } catch (BusinessException domainRejection) {
            // 도메인이 거절했다(재고가 빠졌다, 상태가 바뀌었다 …). 액션은 실패로 닫고, 그
            // 사실을 대화에 남겨 모델이 사용자에게 설명하게 한다. 예외를 그대로 던지면
            // 사용자는 왜 안 되는지 모른 채 카드만 사라진 화면을 보게 된다.
            action.reject(now);
            store.appendAssistant(
                    conversation,
                    "요청을 처리하지 못했습니다: " + domainRejection.getErrorCode().message());
            log.info(
                    "Agent action rejected by domain: id={} code={}",
                    actionId,
                    domainRejection.getErrorCode().code());
            return conversationService.detail(userId, conversation.getId());
        }

        action.complete(result.payloadJson(), now);
        log.info("Agent action completed: id={} tool={}", actionId, action.getToolName());

        // 실행 결과를 이력에 넣어 모델이 다음 말을 이어갈 수 있게 한다.
        store.appendAssistant(conversation, result.userVisibleSummary());
        chatService.resumeAfterAction(userId, conversation.getId());

        return conversationService.detail(userId, conversation.getId());
    }

    /**
     * 다단계 서명의 <b>중간</b> 단계 하나가 끝났다.
     *
     * <p><b>여기서는 LLM 을 부르지 않는다.</b> 생산 확정처럼 4단계짜리 카드에서 단계마다
     * 모델을 부르면 크레딧이 단계 수만큼 든다. 카드 상태만 갱신하고, 대화는 마지막 단계
     * ({@link #complete})에서만 이어간다.
     *
     * <p>도메인 서비스도 부르지 않는다. 중간 단계는 온체인 트랜잭션일 뿐이고, 서버 쪽 상태
     * 변경은 기존 chain-link API 가 이미 처리한다.
     */
    @Transactional
    public void recordStep(Long userId, Long actionId, String resultJson) {
        AgentPendingAction action = pendingActions
                .findById(actionId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_ACTION_NOT_FOUND));
        conversationService.require(userId, action.getConversationId());

        LocalDateTime now = LocalDateTime.now(clock);
        if (!action.isPending() || action.isExpiredAt(now)) {
            action.expire();
            throw new BusinessException(AgentErrorCode.AGENT_ACTION_NOT_PENDING);
        }
        if (action.isLastStep()) {
            // 마지막 단계는 complete 로 와야 한다. 여기서 처리하면 도메인 호출이 빠진다.
            throw new BusinessException(AgentErrorCode.AGENT_ACTION_NOT_PENDING);
        }

        action.advanceStep(now);
        appendStepResult(action, resultJson);
        log.info(
                "Agent action step recorded: id={} step={}/{}",
                actionId,
                action.getStepDone(),
                action.getStepTotal());
    }

    /** 단계 결과를 {@code result_json} 의 배열에 쌓는다. 진행 기록이지 정본이 아니다. */
    private void appendStepResult(AgentPendingAction action, String resultJson) {
        if (!StringUtils.hasText(resultJson)) {
            return;
        }
        try {
            ObjectNode root = action.getResultJson() == null
                    ? objectMapper.createObjectNode()
                    : (ObjectNode) objectMapper.readTree(action.getResultJson());
            ArrayNode completed = root.has("completed")
                    ? (ArrayNode) root.get("completed")
                    : root.putArray("completed");
            completed.add(objectMapper.readTree(resultJson));
            action.replaceResult(root.toString());
        } catch (Exception malformed) {
            // 진행 기록이 깨지는 것으로 서명을 막지 않는다. 정본은 체인이다.
            log.warn("Agent action step result was not valid JSON: id={}", action.getId());
        }
    }

    @Transactional
    public ConversationDetailResponse reject(Long userId, Long actionId) {
        AgentPendingAction action = pendingActions
                .findById(actionId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_ACTION_NOT_FOUND));
        AgentConversation conversation = conversationService.require(userId, action.getConversationId());

        LocalDateTime now = LocalDateTime.now(clock);
        if (!action.isPending() || action.isExpiredAt(now)) {
            action.expire();
            throw new BusinessException(AgentErrorCode.AGENT_ACTION_NOT_PENDING);
        }
        action.reject(now);

        // 거절은 오류가 아니라 선택이다. 모델이 그 사실을 알고 다른 안을 내게 한다.
        store.appendAssistant(conversation, "담당자가 이 작업을 취소했습니다.");
        chatService.resumeAfterAction(userId, conversation.getId());
        return conversationService.detail(userId, conversation.getId());
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
}
