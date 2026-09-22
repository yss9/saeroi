package com.ssafy.upcycle.agent.service;

import com.ssafy.upcycle.agent.AgentErrorCode;
import com.ssafy.upcycle.agent.config.AgentProperties;
import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.repository.AgentConversationRepository;
import com.ssafy.upcycle.agent.service.llm.LlmClient;
import com.ssafy.upcycle.agent.service.llm.LlmCompletion;
import com.ssafy.upcycle.agent.service.llm.ToolCall;
import com.ssafy.upcycle.common.exception.BusinessException;
import java.util.List;
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
 * 일어나기 때문이다. 상한에 걸리면 장애로 취급하지 않고, <b>tool 없이 한 번 더 불러 말로
 * 끝맺는다</b> ({@link #concludeWithoutTools}). 회차를 tool 에 다 써서 결론을 쓸 자리가
 * 없었을 뿐이고, 그때까지 모은 tool 결과는 이력에 그대로 있기 때문이다.
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
        // **사실만 넣는다.** 지금 상태와 그에 따른 지시는 매 턴 시스템 프롬프트로 들어간다
        // ({@code AgentTurnStore.loadPrompt}). 예전에는 그것을 이 문장에 이어 붙였는데,
        // ASSISTANT 역할이라 담당자가 모델용 지시문을 읽었고 모델은 그 블록을 자기 말투로
        // 배워 다음 턴에 통째로 베꼈다.
        turnStore.appendAssistant(conversationId, note);
        runTurn(conversationId, userId);
    }

    private void runTurn(Long conversationId, Long userId) {
        int maxIterations = properties.maxToolIterations();
        // 턴 시작에 한 번만 읽는다. 한 턴 안에서 도메인 상태는 바뀌지 않는다 — 쓰기 tool 은
        // 카드만 만들고 읽기 tool 은 아무것도 바꾸지 않는다.
        String liveState = turnStore.liveState(conversationId);

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            LlmCompletion completion = callModel(conversationId, liveState);

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
        concludeWithoutTools(conversationId, liveState);
    }

    /**
     * 상한에 걸린 턴을 <b>말로 끝맺는다.</b> tool 없이 한 번 더 부른다.
     *
     * <p>루프는 회차마다 "글로 답하거나 / tool 을 부르거나" 둘 중 하나다. 그래서 상한을 tool
     * 에 다 쓰면 결론을 쓸 회차가 남지 않는다. 그때 고정 문구를 내보내면 <b>이미 모아 둔
     * 답을 버리고 사용자를 탓하게 된다.</b>
     *
     * <p>실제로 그랬다. "실크 원단으로 만드는 열쇠고리" 한 마디에 모델이 save_requirement →
     * set_phase → get_my_materials → search_listings 를 불렀고, 마지막 결과는 "조건에 맞는
     * 판매 글이 없습니다. materialType 을 비우고 keyword 로 검색해 보세요" 라는 쓸 만한
     * 답이었다. 그런데 그것을 사람 말로 옮길 회차가 없어 버려지고, 대신 "더 구체적으로
     * 알려주시면" 이 나갔다 — 담당자는 자기가 설명을 못 한 줄 알게 된다.
     *
     * <p>tool 목록을 비워 넘기면 모델은 글로 답할 수밖에 없다({@code LlmClient} 규약).
     * 호출이 한 번 늘지만 tool 명세를 싣지 않으므로 그 한 번은 오히려 가볍다.
     *
     * <p>이 호출까지 실패하거나 빈 답이 오면 그때는 고정 문구로 내려간다. 말없이 끝내면
     * 화면에 아무것도 뜨지 않아 담당자가 무엇을 기다려야 할지 알 수 없다.
     */
    private void concludeWithoutTools(Long conversationId, String liveState) {
        try {
            AgentTurnStore.TurnPrompt prompt = turnStore.loadPrompt(conversationId, liveState);
            LlmCompletion conclusion = llmClient.complete(prompt.messages(), List.of());
            if (StringUtils.hasText(conclusion.content())) {
                turnStore.appendAssistant(conversationId, conclusion.content());
                return;
            }
            log.info("Agent conclusion came back empty: conversationId={}", conversationId);
        } catch (RuntimeException failure) {
            log.warn(
                    "Agent conclusion call failed: conversationId={} reason={} message={}",
                    conversationId,
                    failure.getClass().getSimpleName(),
                    failure.getMessage());
        }
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
    private LlmCompletion callModel(Long conversationId, String liveState) {
        AgentTurnStore.TurnPrompt prompt = turnStore.loadPrompt(conversationId, liveState);
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
