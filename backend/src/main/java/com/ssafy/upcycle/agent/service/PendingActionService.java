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
     * <p><b>실패해도 모델이 이어 말한다.</b> 실패는 담당자가 가장 도움이 필요한 순간인데,
     * 예전에는 그때만 대화가 끊겼다.
     *
     * @param resultJson 화면이 만든 결과(txHash, pgToken, 업로드한 이미지 주소 …).
     *     없을 수도 있다(단순 확인)
     */
    public ConversationDetailResponse complete(Long userId, Long actionId, String resultJson) {
        AgentActionStore.Outcome outcome;
        try {
            outcome = actionStore.apply(userId, actionId, resultJson);
        } catch (AgentActionStore.ActionFailed failed) {
            actionStore.recordFailure(userId, actionId);
            // **실패했을 때야말로 다음에 뭘 해야 하는지 말해야 한다.** 예전에는 실패 문장만
            // 남기고 돌아갔다. 담당자는 "요청을 처리하지 못했습니다: 사용 가능한 수량이
            // 부족합니다" 한 줄을 받고 그 자리에 멈췄다 — 무엇이 잘못됐는지도, 무엇을 하면
            // 되는지도 없었다. 성공과 똑같이 모델이 이어 말하게 한다.
            resumeQuietly(userId, failed.getConversationId(), failed.getMessage());
            return conversationService.detail(userId, failed.getConversationId());
        }

        if (outcome.resumeConversation()) {
            resumeQuietly(userId, outcome.conversationId(), outcome.summary());
        }
        return conversationService.detail(userId, outcome.conversationId());
    }

    /**
     * 승인 뒤 모델이 이어 말하게 한다. <b>여기서 실패해도 승인은 성공이다.</b>
     *
     * <p>승인의 DB 작업은 이미 커밋된 뒤다({@link AgentActionStore#apply}). 그 다음의 LLM
     * 호출은 "모델이 한마디 덧붙이는 것" 이라 본질이 다른데, 예외를 그대로 올리면 <b>이미
     * 끝난 작업이 화면에서 실패로 보인다.</b>
     *
     * <p>실제로 그랬다. 생산 배치 취소 카드를 승인해 배치가 정상적으로 취소됐는데, 이어진
     * 호출이 빈 응답({@code EMPTY_CONTENT})을 받아 요청 전체가 {@code AGENT_LLM_UNAVAILABLE}
     * 로 끝났다. 담당자는 "처리하지 못했습니다" 를 봤지만 배치는 이미 취소돼 있었다.
     * 다시 누르면 이번에는 도메인이 "이미 취소된 배치" 라며 거절한다 — 무엇이 참인지 알
     * 방법이 없어진다.
     *
     * <p>대신 무슨 일이 있었는지 대화에 남긴다. 조용히 넘기면 담당자는 승인 뒤 아무 반응도
     * 없는 화면을 보게 되고, 그것도 실패처럼 읽힌다.
     */
    private void resumeQuietly(Long userId, Long conversationId, String outcome) {
        try {
            chatService.resumeAfterAction(userId, conversationId, outcome);
        } catch (RuntimeException failure) {
            log.warn(
                    "Agent resume after approval failed: conversationId={} reason={} message={}",
                    conversationId,
                    failure.getClass().getSimpleName(),
                    failure.getMessage());
            // **무슨 일이 있었는지는 서버가 안다.** 모델이 말하지 못했다고 담당자가
            // 아무것도 못 보면 안 된다 — 승인은 이미 끝났고, 그 사실이 화면에 없으면
            // 실패로 읽힌다.
            actionStore.appendNotice(userId, conversationId, outcome);
        }
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

    /**
     * 담당자가 카드를 거절했다. 기록은 트랜잭션 안에서, 대화 이어가기는 그 밖에서 한다.
     *
     * <p>거절은 오류가 아니라 선택이다. 무엇을 취소했는지를 모델에게 넘겨 모델이 이어 말하게 한다. 모델이
     * 말하지 못하면 이 문장이 그대로 남는다({@link #resumeQuietly}).
     */
    public ConversationDetailResponse reject(Long userId, Long actionId) {
        AgentActionStore.Rejection rejection = actionStore.recordUserRejection(userId, actionId);
        resumeQuietly(userId, rejection.conversationId(), canceled(rejection.summary()));
        return conversationService.detail(userId, rejection.conversationId());
    }

    static String canceled(String summary) {
        return StringUtils.hasText(summary)
                ? "담당자가 \"%s\" 카드를 취소했습니다.".formatted(summary)
                : "담당자가 카드를 취소했습니다.";
    }
}
