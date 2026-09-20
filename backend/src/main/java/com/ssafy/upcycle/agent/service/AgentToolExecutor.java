package com.ssafy.upcycle.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.AgentConversation;
import com.ssafy.upcycle.agent.entity.AgentMessage;
import com.ssafy.upcycle.agent.entity.AgentPendingAction;
import com.ssafy.upcycle.agent.repository.AgentPendingActionRepository;
import com.ssafy.upcycle.agent.service.llm.ToolCall;
import com.ssafy.upcycle.agent.tool.AgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolArgumentException;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.common.exception.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 모델이 요청한 tool 을 실제로 돌리는 곳. <b>쓰기 경계가 여기 있다.</b>
 *
 * <p>읽기 tool 은 그대로 실행한다. 쓰기 tool 은 실행하지 않고
 * {@link AgentPendingAction} 을 만들어 사용자에게 넘긴다 — 모델이 인자를 잘못 채웠거나
 * 사용자의 말을 오해했을 때 돈이 나가는 것을 구조적으로 막는다. 개별 tool 이 이 판단을
 * 우회할 수 없도록, 분기를 tool 밖인 여기에 둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentToolExecutor {

    private final AgentToolRegistry registry;
    private final AgentPendingActionRepository pendingActions;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 모델의 tool 호출 하나를 처리한다.
     *
     * <p>실패를 예외로 던지지 않고 결과 JSON 으로 돌려주는 경우가 많다. 모델이 인자를 잘못
     * 넣은 것은 장애가 아니라 다음 턴에 고칠 수 있는 일이고, 예외로 끊으면 대화 전체가 죽는다.
     */
    public String execute(
            AgentConversation conversation,
            Long userId,
            ToolCall call,
            AgentMessage toolCallMessage,
            Duration actionTtl) {

        Optional<AgentTool> found = registry.find(call.name(), conversation.getPhase());
        if (found.isEmpty()) {
            log.info("Agent tool rejected: name={} phase={}", call.name(), conversation.getPhase());
            return error("UNKNOWN_TOOL", "지금 단계에서 쓸 수 없는 기능입니다: " + call.name());
        }
        AgentTool tool = found.get();

        JsonNode arguments;
        try {
            arguments = objectMapper.readTree(call.argumentsJson());
        } catch (Exception malformed) {
            // 모델이 만든 JSON 이라 깨질 수 있다. 다시 시도할 수 있게 사실만 알려준다.
            return error("INVALID_ARGUMENTS", "인자를 JSON 으로 읽지 못했습니다. 형식을 확인해 다시 호출하세요.");
        }

        if (tool.isWrite()) {
            // 카드를 띄우기 전에 tool 이 스스로 볼 기회를 준다. 승인 뒤에야 거절하면 담당자는
            // 이미 누른 다음이고, 무엇이 잘못됐는지는 모델이 아니라 오류 화면이 말하게 된다.
            Optional<AgentToolResult> refusal = refusalBeforeProposal(tool, userId, conversation, arguments);
            if (refusal.isPresent()) {
                return refusal.get().payloadJson();
            }
            // 이미 끝난 일이면 카드를 만들지 않는다. 담당자가 화면에서 먼저 처리한 건에
            // 대해 모델이 카드를 띄우는 것이 실제로 있었다 (완료된 거래에 온체인 기록 카드).
            Optional<String> stale = stalenessReason(tool, userId, conversation, arguments);
            if (stale.isPresent()) {
                log.info("Agent tool skipped as stale: name={} reason={}", tool.name(), stale.get());
                return error("ALREADY_DONE", stale.get());
            }
            return propose(conversation, tool, call, arguments, toolCallMessage, actionTtl);
        }

        try {
            AgentToolResult result = tool.execute(new AgentToolContext(userId, conversation), arguments);
            return result.payloadJson();
        } catch (AgentToolArgumentException badArguments) {
            // 모델이 인자를 잘못 채웠다. 무엇이 틀렸는지 알려주면 대개 다음 호출에서 고친다.
            return error("INVALID_ARGUMENTS", badArguments.getMessage());
        } catch (BusinessException domainRejection) {
            // 도메인 규칙 위반(권한 없음, 재고 부족 …)은 모델이 알아야 할 사실이다.
            // 그대로 돌려주면 모델이 사용자에게 이유를 설명하고 다른 수를 제안할 수 있다.
            log.info("Agent tool domain rejection: name={} code={}", tool.name(), domainRejection.getErrorCode().code());
            return error(domainRejection.getErrorCode().code(), domainRejection.getErrorCode().message());
        } catch (RuntimeException failure) {
            log.warn("Agent tool failed: name={} reason={}", tool.name(), failure.getClass().getSimpleName());
            return error("TOOL_FAILED", "기능을 실행하지 못했습니다.");
        }
    }

    /** 사용자가 승인한 액션을 실제로 실행한다. 이 경로만 쓰기 tool 을 돌릴 수 있다. */
    public AgentToolResult runApproved(
            AgentConversation conversation, Long userId, AgentPendingAction action, JsonNode mergedArguments) {

        AgentTool tool = registry
                .findApproved(action.getToolName())
                .orElseThrow(() -> new IllegalStateException("Approved tool disappeared: " + action.getToolName()));

        return tool.execute(new AgentToolContext(userId, conversation), mergedArguments);
    }

    /**
     * 승인된 카드가 이미 지난 일인지.
     *
     * <p>카드를 만들 때와 <b>같은 검사</b>를 쓴다. 두 시점에 서로 다른 판정을 두면 한쪽만
     * 고쳐질 때 구멍이 난다.
     *
     * <p>여기서 예외가 나면 비어 있는 것으로 본다 — 사전 점검이 흔들린다고 담당자가 승인한
     * 작업을 막을 이유는 없다. 못 본 것은 도메인이 거절한다.
     */
    public Optional<String> stalenessReason(
            AgentConversation conversation,
            Long userId,
            AgentPendingAction action,
            JsonNode arguments) {
        return registry
                .findApproved(action.getToolName())
                .flatMap(tool -> stalenessReason(tool, userId, conversation, arguments));
    }

    /** 이미 지난 일인지. 사전 점검이 흔들려도 정상 흐름을 세우지 않는다 — 승인 때 다시 본다. */
    private Optional<String> stalenessReason(
            AgentTool tool, Long userId, AgentConversation conversation, JsonNode arguments) {
        try {
            return tool.stalenessReason(new AgentToolContext(userId, conversation), arguments);
        } catch (RuntimeException failure) {
            log.warn(
                    "Agent staleness check failed, proposing anyway: name={} reason={}",
                    tool.name(),
                    failure.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * 카드를 띄우기 전 거절. 비어 있으면 카드를 만든다.
     *
     * <p>여기서 던져진 예외는 카드를 막지 않는다 — 사전 점검이 흔들린다고 정상 흐름까지
     * 세울 이유는 없다. 못 본 것은 승인 뒤 실행에서 다시 걸린다.
     */
    private Optional<AgentToolResult> refusalBeforeProposal(
            AgentTool tool, Long userId, AgentConversation conversation, JsonNode arguments) {
        try {
            return tool.refuseBeforeProposal(new AgentToolContext(userId, conversation), arguments);
        } catch (RuntimeException failure) {
            log.warn(
                    "Agent pre-check failed, proposing anyway: name={} reason={}",
                    tool.name(),
                    failure.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private String propose(
            AgentConversation conversation,
            AgentTool tool,
            ToolCall call,
            JsonNode arguments,
            AgentMessage toolCallMessage,
            Duration actionTtl) {

        String summary = summarize(tool, arguments);
        AgentPendingAction action = pendingActions.save(AgentPendingAction.propose(
                conversation.getId(),
                toolCallMessage.getId(),
                tool.pendingKind().actionType(),
                tool.name(),
                summary,
                call.argumentsJson(),
                tool.signatureSteps(),
                LocalDateTime.now(clock).plus(actionTtl)));

        log.info(
                "Agent action proposed: id={} tool={} type={}",
                action.getId(),
                tool.name(),
                action.getActionType());

        // 모델에게 "요청을 띄웠고 아직 안 끝났다"는 사실을 분명히 돌려준다. 이 문구가 모호하면
        // 모델이 완료로 착각하고 다음 단계를 진행해 버린다.
        return """
                {"status":"AWAITING_USER_CONFIRMATION","actionId":%d,"actionType":"%s",\
                "message":"담당자에게 확인 카드를 띄웠습니다. 담당자가 승인하기 전까지 이 작업은 \
                수행되지 않았습니다. 결과를 기다리고, 완료된 것처럼 말하지 마세요."}"""
                .formatted(action.getId(), action.getActionType().name());
    }

    /**
     * 카드에 보여줄 한 줄.
     *
     * <p>tool 이 스스로 요약을 만들게 하지 않고 인자를 그대로 쓴다. 요약을 모델이나 tool 이
     * 만들면 카드에 적힌 내용과 실제로 실행될 인자가 어긋날 수 있고, 사용자는 카드만 보고
     * 승인한다. 인자 자체를 보여주는 편이 안전하다.
     */
    private String summarize(AgentTool tool, JsonNode arguments) {
        String rendered = arguments.toString();
        String summary = tool.description() + " / " + rendered;
        return summary.length() > 500 ? summary.substring(0, 500) : summary;
    }

    private String error(String code, String message) {
        return "{\"error\":\"%s\",\"message\":%s}".formatted(code, quote(message));
    }

    private String quote(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception impossible) {
            return "\"\"";
        }
    }
}
