package com.ssafy.upcycle.agent.dto;

import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.entity.ConversationStatus;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import java.time.LocalDateTime;

/** 대화 목록의 한 줄. */
public record ConversationSummaryResponse(
        Long id,
        String title,
        JourneyPhase phase,
        ConversationStatus status,
        LocalDateTime updatedAt) {

    public static ConversationSummaryResponse from(AgentConversation conversation) {
        return new ConversationSummaryResponse(
                conversation.getId(),
                conversation.getTitle(),
                conversation.getPhase(),
                conversation.getStatus(),
                conversation.getUpdatedAt());
    }
}
