package com.ssafy.upcycle.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.upcycle.agent.AgentErrorCode;
import com.ssafy.upcycle.agent.dto.ConversationDetailResponse;
import com.ssafy.upcycle.agent.entity.AgentPendingAction;
import com.ssafy.upcycle.agent.repository.AgentPendingActionRepository;
import com.ssafy.upcycle.common.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 사용자가 확인 카드를 눌렀을 때의 경로. <b>쓰기 tool 이 실제로 도는 유일한 지점이다.</b>
 *
 * <p>모델은 이 클래스를 부를 수 없다. 오직 인증된 HTTP 요청만 여기 닿는다.
 *
 * <h2>트랜잭션 — 이 클래스에는 없다</h2>
 *
 * <p>{@link AgentChatService} 와 같은 이유다. 승인 뒤에는 모델이 다음 말을 하는데 그
 * LLM 호출이 20~30초 걸린다. 그동안 DB 커넥션을 쥐고 있으면 기본 풀 10개가 동시 10명에서
 * 고갈되어 앱 전체가 멈춘다. DB 작업은 {@link AgentActionStore} 의 짧은 트랜잭션들이
 * 맡고, 이 클래스는 그 사이를 잇기만 한다.
 *
 * <p>{@link #recordStep} 만 예외다 — 거기서는 LLM 을 부르지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PendingActionService {

    private final AgentPendingActionRepository pendingActions;
    private final AgentConversationService conversationService;
    private final AgentActionStore actionStore;
    private final AgentChatService chatService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 승인된 액션을 실행하고 대화를 이어간다.
     *
     * <p>실패는 예외로 올라오지 않고 대화에 남는다. 예외를 그대로 던지면 담당자는 왜 안
     * 되는지 모른 채 카드만 사라진 화면을 보게 된다. 그 기록이 실행과 <b>다른 트랜잭션</b>인
     * 이유는 {@link AgentActionStore} 에 적어 두었다.
     *
     * @param resultJson 화면이 만든 결과(txHash, pgToken, 업로드한 이미지 주소 …).
     *     없을 수도 있다(단순 확인)
     */
    public ConversationDetailResponse complete(Long userId, Long actionId, String resultJson) {
        AgentActionStore.Outcome outcome;
        try {
            outcome = actionStore.apply(userId, actionId, resultJson);
        } catch (AgentActionStore.ActionFailed failed) {
            actionStore.recordFailure(userId, actionId, failed.getMessage());
            return conversationService.detail(userId, failed.getConversationId());
        }

        if (outcome.resumeConversation()) {
            chatService.resumeAfterAction(userId, outcome.conversationId());
        }
        return conversationService.detail(userId, outcome.conversationId());
    }

    /**
     * 다단계 서명의 <b>중간</b> 단계 하나가 끝났다.
     *
     * <p><b>여기서는 LLM 을 부르지 않는다.</b> 생산 확정처럼 4단계짜리 카드에서 단계마다
     * 모델을 부르면 크레딧이 단계 수만큼 든다. 카드 상태만 갱신하고, 대화는 마지막 단계
     * ({@link #complete})에서만 이어간다. LLM 을 부르지 않으므로 이 메서드는 트랜잭션을
     * 그대로 둔다.
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

    /** 담당자가 카드를 거절했다. 기록은 트랜잭션 안에서, 대화 이어가기는 그 밖에서 한다. */
    public ConversationDetailResponse reject(Long userId, Long actionId) {
        Long conversationId = actionStore.recordUserRejection(userId, actionId);
        chatService.resumeAfterAction(userId, conversationId);
        return conversationService.detail(userId, conversationId);
    }
}
