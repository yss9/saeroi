package com.ssafy.upcycle.agent.service;

import com.ssafy.upcycle.agent.AgentErrorCode;
import com.ssafy.upcycle.agent.config.AgentProperties;
import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.repository.AgentConversationRepository;
import com.ssafy.upcycle.agent.service.llm.LlmClient;
import com.ssafy.upcycle.agent.service.llm.LlmCompletion;
import com.ssafy.upcycle.agent.service.llm.ToolCall;
import com.ssafy.upcycle.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 한 번의 사용자 발화를 처리하는 오케스트레이터.
 *
 * <h2>루프</h2>
 *
 * <pre>
 *   사용자 발화 저장
 *     └→ (반복) LLM 호출 → tool 호출이 있나?
 *                            ├ 있다 → 읽기면 실행, 쓰기면 카드 생성
 *                            │        결과를 이력에 넣고 다시 호출
 *                            └ 없다 → 그 답이 이 턴의 결론. 저장하고 끝
 * </pre>
 *
 * <p>반복 상한이 있는 이유는 모델이 결론을 못 내고 같은 tool 을 계속 부르는 상황이 실제로
 * 일어나기 때문이다. 상한에 걸리면 장애로 취급하지 않고 담당자에게 되묻는다 — 모델이 스스로
 * 정리하지 못했을 뿐이고, 더 구체적인 입력을 받으면 풀리는 경우가 대부분이다.
 *
 * <h2>트랜잭션 — 이 클래스에는 없다</h2>
 *
 * <p><b>이 클래스의 메서드에 {@code @Transactional} 이 없는 것은 의도다.</b> LLM 호출이
 * 20~30초 걸리는데 그동안 DB 커넥션을 쥐고 있으면, 기본 풀 10개가 동시 10명에서 고갈되어
 * 앱 전체가 멈춘다. 저장은 {@link AgentTurnStore} 의 짧은 트랜잭션들이 맡는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentChatService {

    private final AgentConversationRepository conversations;
    private final AgentTurnStore turnStore;
    private final LlmClient llmClient;
    private final AgentProperties properties;

    /** 대화를 열어 소유자와 활성 여부만 확인한다. 이 읽기만 트랜잭션이다. */
    @Transactional(readOnly = true)
    public Long requireActiveConversation(Long userId, Long conversationId) {
        AgentConversation conversation = conversations
                .findByIdAndBrandUserId(conversationId, userId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.AGENT_CONVERSATION_NOT_FOUND));
        if (!conversation.isActive()) {
            throw new BusinessException(AgentErrorCode.AGENT_CONVERSATION_NOT_ACTIVE);
        }
        return conversation.getId();
    }

    /**
     * 사용자 발화를 받아 한 턴을 돌린다.
     *
     * <p>트랜잭션이 없다. 안에서 여러 개의 짧은 트랜잭션이 열리고 닫힌다.
     */
    public void send(Long userId, Long conversationId, String userText) {
        requireActiveConversation(userId, conversationId);
        turnStore.appendUser(conversationId, userText);
        runTurn(conversationId, userId);
    }

    /**
     * 사용자가 카드를 처리한 뒤 대화를 이어간다.
     *
     * <p>이력에 결과가 이미 들어간 상태로 불린다. 사용자 발화 없이 모델이 다음 말을 하게
     * 하는 것이라 새 사용자 메시지를 넣지 않는다.
     */
    public void resumeAfterAction(Long userId, Long conversationId) {
        runTurn(conversationId, userId);
    }

    /**
     * 대화 밖의 사실을 이력에 넣고 모델이 이어 말하게 한다.
     *
     * <p>{@link #send} 와 달리 사용자 발화가 아니다. 결제 복귀처럼 담당자가 화면에서 끝낸
     * 일을 대화가 알아야 할 때 쓴다.
     */
    public void reportEvent(Long userId, Long conversationId, String note) {
        requireActiveConversation(userId, conversationId);
        turnStore.appendAssistant(conversationId, note);
        runTurn(conversationId, userId);
    }

    private void runTurn(Long conversationId, Long userId) {
        int maxIterations = properties.maxToolIterations();

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            LlmCompletion completion = callModel(conversationId);

            if (!completion.hasToolCalls()) {
                turnStore.appendAssistant(conversationId, completion.content());
                return;
            }

            for (ToolCall call : completion.toolCalls()) {
                turnStore.runTool(
                        conversationId, userId, call, completion.content(), properties.actionTtl());
            }
        }

        // 상한에 걸렸다. 지금까지의 tool 결과는 이력에 남아 있어 다음 턴에 이어갈 수 있다.
        log.info(
                "Agent tool loop hit the limit: conversationId={} limit={}", conversationId, maxIterations);
        turnStore.appendAssistant(
                conversationId,
                "요청을 정리하는 데 시간이 걸리고 있습니다. 조금 더 구체적으로 알려주시면 이어서 진행하겠습니다.");
    }

    /**
     * 이력을 읽어 모델을 부른다.
     *
     * <p>읽기(짧은 트랜잭션)와 호출(트랜잭션 없음)이 나뉘어 있다. 프롬프트를 메모리에
     * 받아둔 뒤 LLM 을 부르므로 호출 동안 커넥션이 반납된 상태다.
     */
    private LlmCompletion callModel(Long conversationId) {
        AgentTurnStore.TurnPrompt prompt = turnStore.loadPrompt(conversationId);
        return llmClient.complete(prompt.messages(), prompt.tools());
    }

    /** 첫 발화로 대화 제목을 만든다. 길면 자른다. */
    static String titleFrom(String firstMessage) {
        if (!StringUtils.hasText(firstMessage)) {
            return "새 대화";
        }
        String condensed = firstMessage.strip().replaceAll("\\s+", " ");
        return condensed.length() <= 60 ? condensed : condensed.substring(0, 60);
    }
}
