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

    private final OpenCards openCards;
    private final AgentConversationRepository conversations;
    private final AgentTurnStore turnStore;
    private final LlmClient llmClient;
    private final AgentProperties properties;
    private final ConversationTurns turns;

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
        // 도는 턴이 끝난 뒤에 담당자의 말을 넣는다. 먼저 넣으면 도는 턴이 그 말까지 읽고 답한 뒤, 이
        // 턴이 같은 답을 한 번 더 한다({@link ConversationTurns}).
        turns.exclusive(
                conversationId,
                () -> {
                    turnStore.appendUser(conversationId, userText);
                    runTurn(conversationId, userId, null);
                },
                outcome -> followUp(conversationId, userId, outcome));
    }

    /**
     * 승인이 끝난 뒤 모델이 <b>그 결과를 담당자에게 알린다.</b>
     *
     * <p>결과 문장을 이력에 넣지 않고 <b>이번 턴의 상태 블록</b>으로 넘긴다. 이력에 넣으면
     * 담당자 화면에 서버 문장이 먼저 뜨고, 모델이 곧이어 같은 말을 한 번 더 한다 — 수령
     * 세 건에 "수령이 블록체인에 기록되고 확정됐습니다" 가 네 번 떴다.
     *
     * <p>TOOL 역할로 넣을 수도 없다. tool 결과는 앞선 tool 호출과 짝이 맞아야 하는데, 카드를
     * 만든 그 호출의 짝은 카드를 띄울 때 이미 썼다. 자동으로 띄운 카드는 애초에 짝이 될
     * 호출이 없다.
     *
     * @param outcome 무슨 일이 일어났는지 한 줄. 모델이 말하지 못하면 이 문장이 그대로 나간다
     */
    public void resumeAfterAction(Long userId, Long conversationId, String outcome) {
        resume(conversationId, userId, outcome);
    }

    /**
     * 대화 밖에서 끝난 일을 모델에게 알리고 이어 말하게 한다.
     *
     * <p>{@link #send} 와 달리 사용자 발화가 아니다. 결제 복귀처럼 담당자가 화면에서 끝낸
     * 일을 대화가 알아야 할 때 쓴다.
     *
     * <p><b>사실을 이력에 먼저 넣지 않는다.</b> 승인 결과와 같은 이유다
     * ({@link #resumeAfterAction(Long, Long, String)}). 예전에는 이 문장을 담당자 화면에 먼저
     * 띄우고 모델을 불렀는데, 모델이 곧이어 "결제가 완료됐습니다" 를 한 번 더 말했다.
     *
     * @param note 무슨 일이 있었는지. 사실만 담는다 — 지금 상태와 그에 따른 지시는 매 턴 시스템
     *     프롬프트로 들어간다({@code AgentTurnStore.loadPrompt})
     */
    public void reportEvent(Long userId, Long conversationId, String note) {
        requireActiveConversation(userId, conversationId);
        try {
            resume(conversationId, userId, note);
        } catch (RuntimeException failure) {
            // **결제는 이미 끝났다.** 모델이 말하지 못했다고 오류를 올리면 결제 복귀 화면이
            // "결제 승인에 실패했습니다" 를 띄운다. 무슨 일이 있었는지는 서버가 안다.
            log.warn(
                    "Agent turn after event failed: conversationId={} reason={} message={}",
                    conversationId,
                    failure.getClass().getSimpleName(),
                    failure.getMessage());
            turnStore.appendAssistant(conversationId, note);
        }
    }

    /**
     * 이어 말하기. 이 대화에서 도는 턴이 있으면 그 턴이 끝난 뒤 모아서 말한다({@link ConversationTurns}).
     * 이번 일로 돈 턴이 실패하면 예외를 올린다 — 호출한 쪽이 결과 문장을 대신 남긴다.
     */
    private void resume(Long conversationId, Long userId, String outcome) {
        turns.resume(
                conversationId,
                outcome,
                fact -> runTurn(conversationId, userId, fact),
                facts -> followUp(conversationId, userId, facts));
    }

    /**
     * 다른 턴이 도는 동안 맡겨 둔 일을 이어 말한다. <b>예외를 올리지 않는다</b> — 맡긴 요청은 이미 돌아가
     * 대신 안내할 자리가 없다. 모델이 말하지 못하면 결과 문장을 그대로 남긴다.
     */
    private void followUp(Long conversationId, Long userId, String outcome) {
        try {
            runTurn(conversationId, userId, outcome, true);
        } catch (RuntimeException failure) {
            log.warn(
                    "Agent follow-up turn failed: conversationId={} reason={} message={}",
                    conversationId,
                    failure.getClass().getSimpleName(),
                    failure.getMessage());
            if (StringUtils.hasText(outcome)) {
                turnStore.appendAssistant(conversationId, outcome);
            }
        }
    }

    private void runTurn(Long conversationId, Long userId, String outcome) {
        runTurn(conversationId, userId, outcome, false);
    }

    /**
     * @param followUp 다른 턴이 도는 동안 끝난 일을 뒤이어 말하는 턴인가. 그렇다면 새로 할 말이 없을 때 조용히 끝날 수
     *     있다({@link #SILENT})
     */
    private void runTurn(Long conversationId, Long userId, String outcome, boolean followUp) {
        int maxIterations = properties.maxToolIterations();
        // 턴 시작에 한 번만 읽는다. 한 턴 안에서 도메인 상태는 바뀌지 않는다 — 쓰기 tool 은
        // 카드만 만들고 읽기 tool 은 아무것도 바꾸지 않는다.
        String liveState = turnStore.liveState(conversationId) + justHappened(outcome)
                + (followUp ? FOLLOW_UP_NOTE : "");

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            LlmCompletion completion = callModel(conversationId, liveState);

            if (!completion.hasToolCalls()) {
                if (StringUtils.hasText(completion.content())) {
                    say(conversationId, completion.content(), followUp);
                    return;
                }
                // 빈 답이다. tool 명세를 빼고 한 번 더 불러 말로 끝맺는다 — 그러지 않으면
                // 담당자는 승인 뒤 아무 반응 없는 화면을 보고, 모델이 하려던 다음 단계도
                // 함께 사라진다.
                log.info("Agent turn got empty content, concluding: conversationId={}", conversationId);
                concludeWithoutTools(conversationId, liveState, outcome, followUp);
                return;
            }

            // 한 번의 답에 도구 호출이 여럿이면 그 답의 글은 첫 호출에만 붙인다. 호출마다 붙이면
            // 이력에 같은 말이 두 번 남아, 모델은 자기가 같은 말을 되풀이한 것으로 읽는다 — 면을
            // 폐소재·신소재로 한꺼번에 찾은 답이 그렇게 두 번 남았다.
            String text = completion.content();
            boolean cardShown = false;
            for (ToolCall call : completion.toolCalls()) {
                String result = turnStore.runTool(conversationId, userId, call, text, properties.actionTtl());
                cardShown |= AgentToolExecutor.awaitsCard(result);
                text = null;
            }
            if (cardShown) {
                // **카드를 띄웠으면 이 턴은 여기서 말로 끝낸다.** 도구를 더 열어 두면 모델은 결과를 확인하겠다며
                // 곧바로 조회하고, 조회에서 "아직" 을 본 뒤에도 다음 일을 미리 써 버렸다 — 한 메시지 안에 "서명이
                // 아직 진행 중입니다" 와 "서명이 완료되었습니다. 다음 단계를 안내하겠습니다" 가 함께 떴고, 생산 확정
                // 카드를 띄우자마자 "생산이 확정됐습니다" 라고 했다. 규칙으로 두 번 막았지만 되풀이됐다.
                concludeWithoutTools(conversationId, liveState + CARD_SHOWN_NOTE, outcome, followUp);
                return;
            }
        }

        // 상한에 걸렸다. 지금까지의 tool 결과는 이력에 남아 있어 다음 턴에 이어갈 수 있다.
        log.info(
                "Agent tool loop hit the limit: conversationId={} limit={}", conversationId, maxIterations);
        concludeWithoutTools(conversationId, liveState, outcome, followUp);
    }

    /**
     * 뒤이어 말하는 턴이 <b>할 말이 없을 때 내는 표시.</b> 이것만 오면 아무것도 남기지 않는다.
     *
     * <p>카드 두 장을 연달아 누르면 첫 턴이 두 결과를 다 보고 말한 뒤, 모아 둔 둘째 결과로 한 턴이 더 돈다. 그 턴은
     * 늘 무언가를 썼다 — "가공이 모두 완료됐습니다…" 바로 뒤에 "가공 작업 8, 9 모두 완료됐습니다…" 가 또 떴다.
     */
    static final String SILENT = "[[조용히]]";

    /** 카드를 띄운 턴의 마지막 말에 붙이는 지시. 도구 없이 부르므로 조회로 새지 않는다. */
    private static final String CARD_SHOWN_NOTE = """


            ## 방금 확인 카드를 띄웠다 — 이번 말은 여기서 끝낸다

            카드가 떴다는 것과 담당자가 카드에서 무엇을 확인하면 되는지만 한두 문장으로 말한다. **카드가 처리된
            뒤의 일은 쓰지 않는다** — "승인됐습니다", "서명이 완료됐습니다", "확정됐습니다", "다음 단계로 넘어갑니다"
            는 담당자가 누르기 전에는 사실이 아니다. 누르면 서버가 알려 주고, 그때 이어서 말한다.""";

    private static final String FOLLOW_UP_NOTE = """


            ## 뒤이어 말하기 — 새로 할 말이 없으면 조용히

            위 일은 바로 앞 안내가 나가는 동안 끝났다. **바로 앞의 당신 말에 이미 담겼으면**(예: 두 건 모두
            끝났다고 이미 말했다) 새로 쓰지 말고 `[[조용히]]` 만 답한다. 담당자는 같은 말을 두 번 읽게 된다.
            새로 알릴 것이 있거나 담당자 차례가 새로 생겼으면 그것만 짧게 쓴다.""";

    /** 모델의 말을 남긴다. 뒤이어 말하는 턴이 조용히 끝나겠다고 하면 남기지 않는다. */
    private void say(Long conversationId, String content, boolean mayStaySilent) {
        if (mayStaySilent && content.contains(SILENT)) {
            String rest = content.replace(SILENT, "").strip();
            if (rest.isEmpty()) {
                log.info("Agent follow-up stayed silent: conversationId={}", conversationId);
                return;
            }
            turnStore.appendAssistant(conversationId, rest);
            return;
        }
        turnStore.appendAssistant(conversationId, content);
    }

    /**
     * 방금 일어난 일 — 담당자가 카드를 승인했거나(성공이든 실패든) 화면에서 끝낸 일(결제
     * 복귀). 모델이 이것을 <b>한 문장으로</b> 전한다.
     *
     * <p>이력이 아니라 상태 블록이라 담당자에게 보이지 않는다. 모델이 이 사실을 자기 말로
     * 한 번만 옮겨 적으면 중복이 생기지 않는다.
     */
    private String justHappened(String outcome) {
        if (!StringUtils.hasText(outcome)) {
            return "";
        }
        return """

                ## 방금 일어난 일 (담당자는 아직 이 문장을 보지 못했다)

                %s

                이 사실을 담당자에게 **한 문장으로** 알리고, **반드시 다음에 무엇을 하면
                되는지 이어서 말한다.** 위에 "바로 다음 할 일" 이 있으면 그 도구를 그 자리에서
                부른다 — 여기서 끝내면 담당자는 여정이 끝난 줄 안다. 없으면 상대를 기다리는
                중인지, 이 프로젝트가 끝났는지를 위 상태에서 읽어 그대로 알린다.

                위 문장이 실패를 말하고 있으면 무엇이 막혔는지 담당자의 말로 풀어 주고, 지금
                할 수 있는 일을 제시한다. 오류 문구를 그대로 옮기지 않는다.

                위 문장이 **담당자가 카드를 취소했다**는 것이면 그 카드를 다시 띄우지 않는다. "바로
                다음 할 일" 이 그 카드여도 마찬가지다 — 담당자가 방금 싫다고 한 것이다. 취소한 것을
                짧게 받아 주고, 어떻게 할지 묻는다.

                위에 여러 줄이 있으면 그동안 여러 일이 끝난 것이다. 한 번에 묶어 말한다.

                위 문장을 그대로 베끼지 말고 대화 흐름에 맞게 쓴다. 같은 내용을 두 번 쓰지 않는다."""
                .formatted(outcome);
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
    private void concludeWithoutTools(Long conversationId, String liveState, String outcome, boolean followUp) {
        try {
            AgentTurnStore.TurnPrompt prompt = turnStore.loadPrompt(conversationId, liveState);
            LlmCompletion conclusion = llmClient.complete(prompt.messages(), List.of());
            if (StringUtils.hasText(conclusion.content())) {
                say(conversationId, conclusion.content(), followUp);
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
        turnStore.appendAssistant(conversationId, lastResort(conversationId, outcome));
    }

    /**
     * 마지막 문구. <b>카드가 떴는지에 따라 달라야 한다.</b>
     *
     * <p>예전에는 언제나 "조금 더 구체적으로 알려주시면" 이라고 했다. 그런데 이 자리에
     * 닿는 흔한 경우가 <b>일을 많이 한 턴</b>이다 — 소재 세 건을 한 번에 사면 결제 카드가
     * 세 장 뜨면서 회차를 다 쓴다. 카드는 멀쩡히 떠 있는데 "더 구체적으로 알려달라" 고 하면
     * 담당자는 요청이 실패한 줄 알고 같은 말을 다시 한다.
     */
    private String lastResort(Long conversationId, String outcome) {
        // 승인 결과가 있으면 그것이 가장 정확한 문장이다. 모델이 말하지 못했다고 담당자가
        // 무슨 일이 일어났는지 모르면 안 된다 — 승인은 이미 끝났다.
        if (StringUtils.hasText(outcome)) {
            return outcome;
        }
        return openCards.of(conversationId).isEmpty()
                ? "요청을 정리하는 데 시간이 걸리고 있습니다. 조금 더 구체적으로 알려주시면 이어서 진행하겠습니다."
                : "요청하신 내용으로 확인 카드를 띄웠습니다. 카드를 확인해 주세요.";
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
