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
import org.springframework.util.StringUtils;

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
            // **어느 단계로 옮기면 되는지 함께 알려준다.** 이름만 거절하면 모델이 "그 기능은
            // 없다" 고 결론 내리고 담당자에게 할 수 없는 일이라고 답한다. 실제로 판매 시작을
            // 그렇게 거절했다 — 단계만 옮기면 되는 일이었다.
            return error("WRONG_PHASE", registry.phaseHintFor(call.name(), conversation.getPhase()));
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
     *
     * <h2>방금 한 서명은 "지난 일" 이 아니다</h2>
     *
     * <p><b>승인이 트랜잭션 해시를 들고 왔으면 판정하지 않는다.</b> 이 검사는 "다른 경로로
     * 이미 처리됐는가" 를 보는 것인데, 서명이 끝난 직후에는 그 다른 경로가 <b>담당자 자신의
     * 서명</b>이다. 구분할 방법이 상태뿐이라 자기 일을 남의 일로 읽는다.
     *
     * <p>실제로 그렇게 깨졌다. 소재 구매 온체인 기록은 서명자가 체인에 주문을 만든 뒤
     * chain-link API 로 서버에 붙이고({@code signTradeOrder}) 나서 카드를 승인한다. 그래서
     * 승인 시점에는 이미 {@code chainTradeId} 가 채워져 있고, 검사가 "이 거래는 이미
     * 블록체인에 기록돼 있어 다시 서명할 필요가 없습니다" 를 돌려준다. 카드는 <b>성공한
     * 서명인데도</b> "이미 처리됨" 으로 닫히고, {@code execute} 가 돌지 않아 "주문이
     * 블록체인에 기록됐습니다 (tx …)" 도 남지 않는다. 담당자는 서명이 헛일이었다고 읽는다.
     *
     * <p>해시가 없는 승인은 그대로 판정한다 — 화면이 {@code alreadyLinked} /
     * {@code alreadyReceived} 로 닫는 건들이 그쪽이고, 그건 정말 남이 먼저 한 경우다.
     *
     * <p>이 완화가 안전한 이유는 검사를 건너뛴 승인도 결국 도메인을 지나기 때문이다. 어긋난
     * 건은 {@code TradeReceiptService.requireChainReceipt} 처럼 <b>체인을 직접 읽는 가드</b>가
     * 거절하고, 그 거절은 {@code AgentActionStore.runOrFail} 이 읽을 수 있는 문장으로 바꾼다.
     */
    public Optional<String> stalenessReason(
            AgentConversation conversation,
            Long userId,
            AgentPendingAction action,
            JsonNode arguments) {
        if (carriesFreshSignature(arguments)) {
            return Optional.empty();
        }
        return registry
                .findApproved(action.getToolName())
                .flatMap(tool -> stalenessReason(tool, userId, conversation, arguments));
    }

    /** 승인과 함께 방금 남긴 트랜잭션 해시가 왔는가. {@code WalletSignCard} 가 넣는다. */
    private boolean carriesFreshSignature(JsonNode arguments) {
        return StringUtils.hasText(arguments.path("_userResult").path("txHash").asText(null));
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
        //
        // **cardId 는 도메인 번호가 아니라고 못 박는다.** 예전에는 이 값을 actionId 라는
        // 이름으로만 돌려줬는데, 승인 뒤 실행 결과가 이력에 들어가지 않아 모델이 본 숫자가
        // 이것뿐이었다. 그래서 그것을 도메인 번호로 썼다 — 카드 44번을 승인해 생산 배치
        // 3번이 열렸는데 add_production_inputs 에 batchId:44 를 넘겨 "생산 배치를 찾을 수
        // 없습니다" 로 끝났다. 번호는 시스템 프롬프트의 실제 상태 목록이 알려준다
        // (AgentPendingWork). 여기서는 헷갈릴 여지를 지운다.
        return """
                {"status":"AWAITING_USER_CONFIRMATION","cardId":%d,"actionType":"%s",\
                "message":"담당자에게 확인 카드를 띄웠습니다. 담당자가 승인하기 전까지 이 작업은 \
                수행되지 않았습니다. 결과를 기다리고, 완료된 것처럼 말하지 마세요. \
                cardId 는 이 카드의 번호일 뿐입니다 — 거래·작업·배치 번호로 쓰지 마세요. \
                그 번호는 조회 도구나 시스템 프롬프트의 현재 상태 목록에서 확인하세요."}"""
                .formatted(action.getId(), action.getActionType().name());
    }

    /**
     * 카드에 보일 한 줄.
     *
     * <p><b>모델이 준 {@code summary} 를 먼저 쓴다.</b> 쓰기 tool 은 대부분 그 인자를
     * 요구하고, 담당자가 읽을 문장으로 쓰라고 스키마에 적어 두었다. 예전에는 tool 설명과
     * 인자 JSON 을 이어 붙였는데, 카드에 "배송된 소재의 수령을 확정한다. 소유권이
     * 이전되고… / {\"tradeId\":16,…}" 가 그대로 떠서 읽히지 않았다.
     *
     * <p>없을 때만 예전 방식으로 떨어진다 — 그때라도 무엇을 하려는지는 보여야 한다.
     */
    private String summarize(AgentTool tool, JsonNode arguments) {
        String provided = arguments.path("summary").asText(null);
        if (provided != null && !provided.isBlank()) {
            return provided.length() > 500 ? provided.substring(0, 500) : provided;
        }
        String summary = tool.description() + " / " + arguments;
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
