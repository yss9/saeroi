package com.ssafy.upcycle.agent.service;

import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.entity.AgentMessage;
import com.ssafy.upcycle.agent.repository.AgentConversationRepository;
import com.ssafy.upcycle.agent.service.llm.LlmMessage;
import com.ssafy.upcycle.agent.service.llm.LlmRole;
import com.ssafy.upcycle.agent.service.llm.ToolCall;
import com.ssafy.upcycle.agent.service.llm.ToolSpec;
import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    private final AgentPendingWork pendingWork;

    /**
     * 서버가 방금 조회한 실제 상태. <b>한 턴에 한 번만 부른다.</b>
     *
     * <p>도메인 상태는 한 턴 안에서 바뀌지 않는다 — 쓰기 tool 은 실행되지 않고 카드만 만들고,
     * 읽기 tool 은 아무것도 바꾸지 않는다. 상태가 움직이는 것은 담당자가 카드를 승인하는
     * 별도의 요청이고, 그때는 새 턴이 시작된다.
     *
     * <p>그래서 회차마다 다시 조회하면 같은 답을 여러 번 받는 낭비다. 거래·가공·생산·제품 네
     * 갈래를 보고 그 중 배치 조회는 배치마다 투입을 또 읽으므로, 상한 6회 기준으로 한 발화에
     * 수십 번의 질의가 됐다.
     */
    @Transactional(readOnly = true)
    public String liveState(Long conversationId) {
        AgentConversation conversation = load(conversationId);
        return pendingWork.describe(conversation.getBrandUserId(), conversation.getId());
    }

    /**
     * 모델에 넣을 프롬프트. {@code liveState} 는 {@link #liveState} 가 턴 시작에 한 번 읽은 값이다.
     *
     * <p><b>막힌 단계는 시스템 프롬프트로 넣는다.</b> 예전에는 이 목록을 ASSISTANT 메시지로
     * 이력에 넣었는데(reportEvent), 두 가지가 동시에 망가졌다.
     *
     * <p>하나, 담당자가 그걸 읽었다. "request_chain_order 로 서명 카드를 띄운다", "이 목록이
     * 정본이다" 같은 모델용 지시문이 대화창에 그대로 떴다.
     *
     * <p>둘, 모델이 그걸 <b>자기 말투로 배웠다.</b> ASSISTANT 역할이라 "내가 평소에 하는 말"
     * 로 보였고, 다음 턴에 그 블록을 통째로 베껴 거래 번호만 바꿔 출력했다 — 아직 결제도 안 된
     * 거래에 대해 "결제는 끝났고 온체인 기록이 아직이다" 라는 없는 사실까지 지어냈다.
     *
     * <p>그리고 이 자리여야 <b>매 턴</b> 들어간다. 이력에 넣던 방식은 결제 복귀 같은 특정
     * 순간에만 들어가서, 그 순간을 놓친 단계는 그 뒤로 모델의 시야에서 사라졌다. 실제로 결제만
     * 되고 온체인 주문이 없는 거래가 그렇게 잊혔다.
     *
     * <p>이력은 회차마다 다시 읽는다 — tool 결과가 붙기 때문이다.
     *
     * <p><b>이력을 메모리에 담아 반환하는 것이 핵심이다.</b> 호출자는 이 값을 들고 트랜잭션
     * <b>밖</b>에서 LLM 을 부르므로, 20~30초 동안 커넥션이 반납된 상태가 된다.
     */
    @Transactional(readOnly = true)
    public TurnPrompt loadPrompt(Long conversationId, String liveState) {
        AgentConversation conversation = load(conversationId);
        List<LlmMessage> messages = new ArrayList<>();
        messages.add(LlmMessage.system(promptBuilder.build(conversation)));
        if (StringUtils.hasText(liveState)) {
            messages.add(LlmMessage.system(liveState));
        }
        messages.addAll(store.toLlmMessages(store.history(conversationId)));
        if (endsWithOwnWords(messages)) {
            messages.add(LlmMessage.user(SCREEN_EVENT_NOTICE));
        }
        return new TurnPrompt(messages, registry.specs());
    }

    /**
     * 담당자의 말 없이 모델을 부를 때 마지막에 붙이는 알림. <b>이력에 저장하지 않는다.</b>
     *
     * <p>카드를 승인하거나 결제를 마치고 돌아오면 모델이 이어 말해야 하는데, 그때 이력의 마지막은
     * 모델 자신의 말이다. 요청이 모델의 말로 끝나면 API 는 그것을 <b>이어 쓸 문장</b>으로 받는다 —
     * 이미 끝난 문장이라 모델은 아무것도 덧붙이지 않았다. 실제로 승인 뒤 매 턴이 빈 답으로 끝나
     * ("Agent turn got empty content"), 담당자는 모델의 안내 대신 서버가 대신 쓴 문장만 봤다 —
     * "결제가 완료되었습니다. (거래 30번)", "주문이 블록체인에 기록됐습니다. (tx 0x…)".
     *
     * <p>담당자가 쓴 말로 보이지 않게 대괄호로 밝힌다. 무엇이 일어났는지는 시스템 프롬프트의
     * "방금 일어난 일" 이 알려 준다({@code AgentChatService.justHappened}).
     */
    private static final String SCREEN_EVENT_NOTICE =
            "[화면 알림 — 담당자가 입력한 말이 아닙니다] 담당자가 화면에서 방금 카드를 처리했거나 결제·서명을 "
                    + "마쳤습니다. 시스템 프롬프트의 \"방금 일어난 일\" 과 현재 상태를 보고 담당자에게 이어서 안내하세요.";

    /** 요청이 모델 자신의 말로 끝나는가. 도구 결과로 끝나면 모델이 이어 말할 차례라 괜찮다. */
    private static boolean endsWithOwnWords(List<LlmMessage> messages) {
        if (messages.isEmpty()) {
            return false;
        }
        LlmMessage last = messages.getLast();
        return last.role() == LlmRole.ASSISTANT && !last.hasToolCalls();
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
     *
     * @return 모델에게 돌려준 결과. 카드가 떴는지 여기서 안다({@link AgentToolExecutor#awaitsCard})
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public String runTool(Long conversationId, Long userId, ToolCall call, String assistantText, Duration actionTtl) {
        AgentConversation conversation = load(conversationId);
        AgentMessage callMessage = store.appendToolCall(conversation, assistantText, call);
        String result = executor.execute(conversation, userId, call, callMessage, actionTtl);
        store.appendToolResult(conversation, call, result);
        return result;
    }

    private AgentConversation load(Long conversationId) {
        return conversations
                .findById(conversationId)
                .orElseThrow(() -> new IllegalStateException("Conversation disappeared: " + conversationId));
    }
}
