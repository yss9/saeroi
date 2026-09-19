package com.ssafy.upcycle.agent.service;

import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.entity.AgentMessage;
import com.ssafy.upcycle.agent.entity.AgentMessageRole;
import com.ssafy.upcycle.agent.repository.AgentMessageRepository;
import com.ssafy.upcycle.agent.service.llm.LlmMessage;
import com.ssafy.upcycle.agent.service.llm.ToolCall;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 대화 이력의 저장과 복원.
 *
 * <p>여기가 맡는 어려운 일은 하나다 — <b>이력이 길어졌을 때 무엇을 버릴지.</b> 여정 전체가 한
 * 대화라 메시지가 수백 개가 되고, 전부 넣으면 컨텍스트 한도를 넘는다. 자세한 규칙은
 * {@link #toLlmMessages} 주석에 있다.
 */
@Service
@RequiredArgsConstructor
public class ConversationStore {

    /**
     * 모델에 넣을 최근 메시지 수 상한.
     *
     * <p>토큰이 아니라 개수로 자르는 것은 근사다. 정확히 하려면 토크나이저가 필요한데, GMS 의
     * 토크나이저가 무엇인지 확정되지 않았다. 개수로 자르고 상한을 보수적으로 잡는 편이,
     * 틀린 토크나이저로 정확한 척하는 것보다 낫다.
     *
     * <p><b>20 으로 줄였다.</b> 크레딧이 토큰당 차감인데 tool 루프는 한 턴에 이력을 여러 번
     * 다시 보낸다 — 이력이 길면 그 배수만큼 곱해진다. 잘려 나간 앞부분의 요구사항은
     * {@code conversation.requirementJson} 으로 시스템 프롬프트에 다시 들어가므로, 대화가
     * 무엇을 하기로 했는지는 잊지 않는다.
     */
    private static final int MAX_HISTORY_MESSAGES = 20;

    private final AgentMessageRepository messages;
    private final Clock clock;

    public List<AgentMessage> history(Long conversationId) {
        return messages.findByConversationIdOrderBySequenceNoAsc(conversationId);
    }

    public AgentMessage appendUser(AgentConversation conversation, String content) {
        return messages.save(
                AgentMessage.user(conversation.getId(), nextSequence(conversation.getId()), content, now()));
    }

    public AgentMessage appendAssistant(AgentConversation conversation, String content) {
        return messages.save(
                AgentMessage.assistant(conversation.getId(), nextSequence(conversation.getId()), content, now()));
    }

    public AgentMessage appendToolCall(AgentConversation conversation, String content, ToolCall call) {
        return messages.save(AgentMessage.assistantToolCall(
                conversation.getId(),
                nextSequence(conversation.getId()),
                content,
                call.name(),
                call.id(),
                call.argumentsJson(),
                now()));
    }

    public AgentMessage appendToolResult(AgentConversation conversation, ToolCall call, String resultJson) {
        return messages.save(AgentMessage.toolResult(
                conversation.getId(),
                nextSequence(conversation.getId()),
                call.name(),
                call.id(),
                resultJson,
                now()));
    }

    /**
     * 저장된 이력을 모델이 읽을 형태로 바꾼다.
     *
     * <p><b>자를 때의 규칙.</b> 최근 {@value #MAX_HISTORY_MESSAGES} 개만 남기되, 앞쪽을 자를 때
     * tool 결과가 그 호출보다 먼저 나오는 상태로 시작하면 안 된다. OpenAI 호환 API 는 짝이
     * 맞지 않는 {@code tool} 메시지를 거부한다. 그래서 잘라낸 첫 메시지가 고아 tool 결과이면
     * 짝이 맞을 때까지 더 버린다.
     *
     * <p>버려진 앞부분의 내용이 완전히 사라지지는 않는다 — 요구사항은
     * {@code conversation.requirementJson} 에 요약돼 시스템 프롬프트로 다시 들어간다.
     */
    public List<LlmMessage> toLlmMessages(List<AgentMessage> stored) {
        Deque<AgentMessage> window = new ArrayDeque<>();
        for (int i = stored.size() - 1; i >= 0 && window.size() < MAX_HISTORY_MESSAGES; i--) {
            window.addFirst(stored.get(i));
        }
        // 짝 없는 tool 결과로 시작하면 버린다.
        while (!window.isEmpty() && window.peekFirst().getRole() == AgentMessageRole.TOOL) {
            window.removeFirst();
        }

        List<LlmMessage> converted = new ArrayList<>(window.size());
        for (AgentMessage message : window) {
            converted.add(toLlmMessage(message));
        }
        return converted;
    }

    private LlmMessage toLlmMessage(AgentMessage message) {
        return switch (message.getRole()) {
            case USER -> LlmMessage.user(message.getContent());
            case TOOL -> LlmMessage.toolResult(message.getToolCallId(), message.getToolPayloadJson());
            case ASSISTANT -> message.isToolCall()
                    ? LlmMessage.assistantToolCalls(
                            message.getContent(),
                            List.of(new ToolCall(
                                    message.getToolCallId(),
                                    message.getToolName(),
                                    message.getToolPayloadJson())))
                    : LlmMessage.assistant(message.getContent());
        };
    }

    private int nextSequence(Long conversationId) {
        Integer max = messages.findMaxSequenceNo(conversationId);
        return max == null ? 0 : max + 1;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
