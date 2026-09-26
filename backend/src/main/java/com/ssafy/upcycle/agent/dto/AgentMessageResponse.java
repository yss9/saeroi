package com.ssafy.upcycle.agent.dto;

import com.ssafy.upcycle.agent.entity.AgentMessage;
import com.ssafy.upcycle.agent.entity.AgentMessageRole;
import java.time.LocalDateTime;

/**
 * 화면에 그릴 메시지 한 줄.
 *
 * <p>{@code toolPayloadJson} 을 내보내지 않는다. 화면이 tool 의 원시 결과를 그릴 일이 없고,
 * 그 안에는 모델에게만 필요한 내부 식별자가 섞여 있다. 필요한 정보는 모델이 말로 풀어
 * {@code content} 에 담거나 카드({@link PendingActionResponse})로 나간다.
 */
public record AgentMessageResponse(
        Long id, AgentMessageRole role, String content, String toolName, LocalDateTime createdAt) {

    public static AgentMessageResponse from(AgentMessage message) {
        return new AgentMessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getToolName(),
                message.getCreatedAt());
    }
}
