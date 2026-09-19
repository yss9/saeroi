package com.ssafy.upcycle.agent.service;

import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.entity.AgentMessage;
import com.ssafy.upcycle.agent.repository.AgentConversationRepository;
import com.ssafy.upcycle.agent.service.llm.LlmMessage;
import com.ssafy.upcycle.agent.service.llm.ToolCall;
import com.ssafy.upcycle.agent.service.llm.ToolSpec;
import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 턴 중간의 짧은 쓰기들. <b>LLM 호출을 트랜잭션 밖으로 밀어내기 위해 존재한다.</b>
 *
 * <p>턴 전체를 하나의 {@code @Transactional} 로 감싸면 DB 커넥션을 LLM 응답이 올 때까지
 * (20~30초) 쥐고 있게 된다. 커넥션 풀이 기본 10개라 <b>동시 10명이 대화하면 풀이 고갈되고
 * 소재 등록·결제·로그인까지 앱 전체가 멈춘다.</b>
 *
 * <p>그래서 {@link AgentChatService} 의 루프는 트랜잭션 없이 돌고, 저장이 필요한 순간에만
 * 이 클래스의 메서드를 불러 짧은 트랜잭션을 연다. 각 메서드는 {@code REQUIRES_NEW} 가 아니라
 * {@code REQUIRED} 다 — 호출자에게 트랜잭션이 없으므로 새로 열리고, 메서드가 끝나면 바로
 * 커밋되며 커넥션이 반납된다.
 *
 * <p>대가는 원자성이다. 턴 중간에 실패하면 반쯤 진행된 이력이 남는다. 이건 감당한다 —
 * 다음 턴에 모델이 그 이력을 보고 이어가면 되고, 진행이 보존되는 편이 사용자에게 낫다.
 * <b>확인 카드 승인의 원자성은 여기와 무관하게 유지된다</b> ({@code PendingActionService}).
 */
@Service
@RequiredArgsConstructor
public class AgentTurnStore {

    private final ConversationStore store;
    private final AgentToolExecutor executor;
    private final AgentToolRegistry registry;
    private final AgentPromptBuilder promptBuilder;
    private final AgentConversationRepository conversations;

    /**
     * 모델에 넣을 프롬프트를 통째로 만들어 돌려준다.
     *
     * <p>이력을 메모리에 담아 반환하는 것이 핵심이다. 호출자는 이 값을 들고 트랜잭션 <b>밖</b>
     * 에서 LLM 을 부르므로, 20~30초 동안 커넥션이 반납된 상태가 된다.
     */
    @Transactional(readOnly = true)
    public TurnPrompt loadPrompt(Long conversationId) {
        AgentConversation conversation = load(conversationId);
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(LlmMessage.system(promptBuilder.build(conversation)));
        messages.addAll(store.toLlmMessages(store.history(conversationId)));
        return new TurnPrompt(messages, registry.specsFor(conversation.getPhase()));
    }

    /** 한 번의 LLM 호출에 넘길 것. */
    public record TurnPrompt(List<LlmMessage> messages, List<ToolSpec> tools) {}

    @Transactional(propagation = Propagation.REQUIRED)
    public void appendUser(Long conversationId, String content) {
        store.appendUser(load(conversationId), content);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void appendAssistant(Long conversationId, String content) {
        store.appendAssistant(load(conversationId), content);
    }

    /**
     * tool 호출과 그 결과를 한 트랜잭션에서 저장한다.
     *
     * <p>둘을 나누면 호출만 저장되고 결과가 빠진 이력이 생길 수 있는데, 그 상태를 모델에
     * 넣으면 짝이 맞지 않는 tool 메시지가 되어 API 가 거부한다.
     *
     * <p>tool 실행 자체(도메인 서비스 호출)도 이 트랜잭션 안에서 일어난다. 읽기 tool 은
     * 짧고, 쓰기 tool 은 실행되지 않고 카드만 만들므로 길어지지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void runTool(Long conversationId, Long userId, ToolCall call, String assistantText, Duration actionTtl) {
        AgentConversation conversation = load(conversationId);
        AgentMessage callMessage = store.appendToolCall(conversation, assistantText, call);
        String result = executor.execute(conversation, userId, call, callMessage, actionTtl);
        store.appendToolResult(conversation, call, result);
    }

    private AgentConversation load(Long conversationId) {
        return conversations
                .findById(conversationId)
                .orElseThrow(() -> new IllegalStateException("Conversation disappeared: " + conversationId));
    }
}
