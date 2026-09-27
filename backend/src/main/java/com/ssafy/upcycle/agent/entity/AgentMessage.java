package com.ssafy.upcycle.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 대화 이력 한 줄. 다음 턴에 LLM 에게 그대로 되돌려 넣는 값이므로 형태를 함부로 바꾸지 않는다.
 *
 * <p>{@code sequenceNo} 로 정렬하고 {@code createdAt} 으로 정렬하지 않는다. 한 턴 안에서
 * assistant 메시지와 tool 결과가 같은 초에 여러 개 쌓이는데, 그때 시각만으로는 순서가
 * 흔들린다. 순서가 어긋난 이력을 모델에 넣으면 tool 호출과 결과의 짝이 깨진다.
 */
@Getter
@Entity
@Table(
        name = "agent_message",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_agent_message_sequence",
                        columnNames = {"conversation_id", "sequence_no"}),
        indexes =
                @Index(
                        name = "idx_agent_message_conversation",
                        columnList = "conversation_id,sequence_no"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentMessage {

    private static final int TOOL_NAME_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private AgentMessageRole role;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "tool_name", length = TOOL_NAME_MAX_LENGTH)
    private String toolName;

    /** 모델이 붙인 tool 호출 식별자. 결과를 되돌려줄 때 이 값으로 짝을 맞춘다. */
    @Column(name = "tool_call_id", length = TOOL_NAME_MAX_LENGTH)
    private String toolCallId;

    @Column(name = "tool_payload_json", columnDefinition = "TEXT")
    private String toolPayloadJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static AgentMessage user(Long conversationId, int sequenceNo, String content, LocalDateTime createdAt) {
        AgentMessage message = base(conversationId, sequenceNo, AgentMessageRole.USER, createdAt);
        message.content = requireText(content, "content");
        return message;
    }

    /** 모델이 말로 답한 턴. */
    public static AgentMessage assistant(
            Long conversationId, int sequenceNo, String content, LocalDateTime createdAt) {
        AgentMessage message = base(conversationId, sequenceNo, AgentMessageRole.ASSISTANT, createdAt);
        message.content = content;
        return message;
    }

    /**
     * 모델이 tool 을 부르기로 한 턴.
     *
     * <p>{@code content} 가 비어 있을 수 있다 — 모델이 설명 없이 곧장 tool 만 부르는 경우가
     * 흔하다. 그래서 이 필드는 nullable 이다.
     */
    public static AgentMessage assistantToolCall(
            Long conversationId,
            int sequenceNo,
            String content,
            String toolName,
            String toolCallId,
            String argumentsJson,
            LocalDateTime createdAt) {
        AgentMessage message = base(conversationId, sequenceNo, AgentMessageRole.ASSISTANT, createdAt);
        message.content = content;
        message.toolName = requireToolName(toolName);
        message.toolCallId = requireToolName(toolCallId);
        message.toolPayloadJson = requireText(argumentsJson, "tool arguments");
        return message;
    }

    /** tool 실행 결과. */
    public static AgentMessage toolResult(
            Long conversationId,
            int sequenceNo,
            String toolName,
            String toolCallId,
            String resultJson,
            LocalDateTime createdAt) {
        AgentMessage message = base(conversationId, sequenceNo, AgentMessageRole.TOOL, createdAt);
        message.toolName = requireToolName(toolName);
        message.toolCallId = requireToolName(toolCallId);
        message.toolPayloadJson = requireText(resultJson, "tool result");
        return message;
    }

    public boolean isToolCall() {
        return role == AgentMessageRole.ASSISTANT && toolCallId != null;
    }

    private static AgentMessage base(
            Long conversationId, int sequenceNo, AgentMessageRole role, LocalDateTime createdAt) {
        if (conversationId == null || conversationId <= 0) {
            throw new IllegalArgumentException("Message conversation id must be positive");
        }
        if (sequenceNo < 0) {
            throw new IllegalArgumentException("Message sequence must not be negative");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Message created at must not be null");
        }
        AgentMessage message = new AgentMessage();
        message.conversationId = conversationId;
        message.sequenceNo = sequenceNo;
        message.role = role;
        message.createdAt = createdAt;
        return message;
    }

    private static String requireToolName(String value) {
        if (value == null || value.isBlank() || value.length() > TOOL_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Message tool name must be 1.." + TOOL_NAME_MAX_LENGTH + " characters");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Message " + field + " must not be blank");
        }
        return value;
    }
}
