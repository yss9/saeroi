package com.ssafy.upcycle.agent.dto;

import com.ssafy.upcycle.agent.entity.AgentPendingAction;
import com.ssafy.upcycle.agent.entity.PendingActionStatus;
import com.ssafy.upcycle.agent.entity.PendingActionType;
import java.time.LocalDateTime;

/**
 * 대화 말풍선 안에 뜨는 확인 카드.
 *
 * <p>{@code payloadJson} 을 그대로 내보낸다. 화면이 서명이나 결제를 만들려면 실제 인자가
 * 필요하고, 담당자가 무엇에 동의하는지도 이 값에서 나온다.
 *
 * <p>{@code resultJson} 은 실행 결과다. 결제 카드의 경우 여기에 카카오페이 결제창 주소가
 * 들어오므로 화면이 그리로 이동한다. 승인 전에는 {@code null} 이다.
 *
 * @param stepDone 표시용이다. 다단계 서명에서 <b>다음에 할 단계는 온체인 상태로 정한다</b> —
 *     트랜잭션은 성공했는데 서버 보고가 실패하면 이 값이 체인보다 뒤처지고, 그걸 믿으면
 *     이미 끝난 단계를 다시 서명해 revert 한다
 */
public record PendingActionResponse(
        Long id,
        Long messageId,
        PendingActionType actionType,
        String toolName,
        String summary,
        String payloadJson,
        String resultJson,
        PendingActionStatus status,
        int stepTotal,
        int stepDone,
        LocalDateTime expiresAt) {

    public static PendingActionResponse from(AgentPendingAction action) {
        return new PendingActionResponse(
                action.getId(),
                action.getMessageId(),
                action.getActionType(),
                action.getToolName(),
                action.getSummary(),
                action.getPayloadJson(),
                action.getResultJson(),
                action.getStatus(),
                action.getStepTotal(),
                action.getStepDone(),
                action.getExpiresAt());
    }
}
