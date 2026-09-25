package com.ssafy.upcycle.agent.dto;

import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.entity.AgentMessage;
import com.ssafy.upcycle.agent.entity.AgentMessageRole;
import com.ssafy.upcycle.agent.entity.AgentPendingAction;
import com.ssafy.upcycle.agent.entity.ConversationStatus;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import java.util.List;
import java.util.Set;

/**
 * 대화 화면이 한 번에 받는 전체 상태.
 *
 * @param projectTradeIds 이 대화에서 산 거래 번호. 오른쪽 할 일 목록이 이것만 보인다 — 계정의
 *     모든 거래를 보이면 홈페이지에서 산 다른 소재의 "수령 확정" 이 이 제품의 할 일처럼 뜬다
 */
public record ConversationDetailResponse(
        Long id,
        String title,
        JourneyPhase phase,
        ConversationStatus status,
        List<AgentMessageResponse> messages,
        List<PendingActionResponse> actions,
        List<Long> projectTradeIds) {

    public static ConversationDetailResponse of(
            AgentConversation conversation,
            List<AgentMessage> messages,
            List<AgentPendingAction> actions,
            Set<Long> projectTradeIds) {
        return new ConversationDetailResponse(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getPhase(),
                conversation.getStatus(),
                messages.stream()
                        // tool 호출/결과 줄은 화면에 그리지 않는다. 사용자가 읽을 것은 모델의
                        // 말과 카드이고, 중간 과정을 다 보여주면 대화가 로그처럼 보인다.
                        .filter(message -> message.getRole() != AgentMessageRole.TOOL)
                        .filter(message -> !message.isToolCall())
                        .map(AgentMessageResponse::from)
                        .toList(),
                actions.stream().map(PendingActionResponse::from).toList(),
                projectTradeIds.stream().sorted().toList());
    }
}
