package com.ssafy.upcycle.agent.tool.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.PendingActionKind;
import com.ssafy.upcycle.processing.dto.ProcessingJobResponse;
import com.ssafy.upcycle.processing.service.ProcessingJobWorkflowService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 가공 작업을 <b>블록체인에 등록한다.</b> 소재 인계보다 앞선 단계다.
 *
 * <h2>왜 인계와 나뉘어 있나 — 사이에 업체 차례가 있다</h2>
 *
 * <p>컨트랙트의 순서가 이렇다.
 *
 * <pre>
 *   브랜드 createProcessingJob  → REQUESTED
 *   업체   acceptProcessingJob  → ACCEPTED
 *   브랜드 markShipped          → SHIPPED   (ACCEPTED 일 때만 받는다)
 *   업체   confirmReceipt       → RECEIVED
 * </pre>
 *
 * <p>즉 <b>브랜드가 작업을 등록한 뒤, 업체가 체인에서 수락해야</b> 발송 서명을 할 수 있다.
 * 결제 뒤의 수락은 DB 상태일 뿐 체인과 별개다.
 *
 * <p>예전에는 인계 카드 하나가 등록과 발송을 함께 했다. 그러면 <b>구조적으로 성공할 수
 * 없다</b> — 방금 만든 작업은 {@code REQUESTED} 이고 그 사이에 업체가 수락할 틈이 없다.
 * 실제로 {@code execution reverted: "Processing: invalid status"} 라는 원시 문자열이
 * 담당자 화면에 그대로 떴고, 두 번 눌러도 같은 결과였다.
 *
 * <p>화면(가공 작업 상세)은 처음부터 이 둘을 나눠 두고 있었다 — "MetaMask로 온체인 작업
 * 등록" 버튼과 발송 폼이 따로다. 대화도 같은 모양으로 맞춘다.
 */
@Component
public class RegisterProcessingChainJobTool extends AbstractAgentTool {

    private final ProcessingJobWorkflowService jobWorkflow;

    public RegisterProcessingChainJobTool(
            ObjectMapper objectMapper, ProcessingJobWorkflowService jobWorkflow) {
        super(objectMapper);
        this.jobWorkflow = jobWorkflow;
    }

    @Override
    public String name() {
        return "register_processing_chain_job";
    }

    @Override
    public String description() {
        return "가공 작업을 블록체인에 등록하도록 담당자에게 서명 카드를 띄운다. "
                + "업체가 결제를 수락한 뒤 소재를 인계하기 전에 한 번 필요하다. "
                + "등록하면 투입 소재가 온체인에 잠기고, 그다음 업체가 체인에서 수락해야 "
                + "인계 서명을 할 수 있다 — 등록과 인계는 다른 단계다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "jobId": {"type": "integer", "description": "업체가 수락한 가공 작업 id"},
                    "summary": {"type": "string",
                      "description": "담당자가 무엇에 서명하는지 한 줄로. 카드에 그대로 보인다"}
                  },
                  "required": ["jobId", "summary"],
                  "additionalProperties": false
                }""";
    }

    @Override
    public boolean isWrite() {
        return true;
    }

    @Override
    public PendingActionKind pendingKind() {
        return PendingActionKind.WALLET_SIGN;
    }

    /**
     * 이미 등록된 작업인가.
     *
     * <p>두 번 등록하면 체인에 같은 작업이 두 개 생기고, 그중 어느 쪽이 정본인지 알 수 없다.
     * 서버가 먼저 걸러 카드를 만들지 않는다.
     */
    @Override
    public Optional<String> stalenessReason(AgentToolContext context, JsonNode arguments) {
        long jobId = requiredPositiveLong(arguments, "jobId");
        ProcessingJobResponse job = jobWorkflow.list(context.userId()).stream()
                .filter(each -> each.jobId() == jobId)
                .findFirst()
                .orElse(null);
        if (job == null || job.chainJobId() == null) {
            return Optional.empty();
        }
        return Optional.of(
                "이 작업은 이미 블록체인에 등록돼 있습니다. 다음은 업체의 온체인 수락이고, "
                        + "그 뒤에 request_custody_transfer 로 소재를 인계합니다.");
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        long jobId = requiredPositiveLong(arguments, "jobId");
        String txHash = arguments.path("_userResult").path("txHash").asText(null);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jobId", jobId);
        payload.put("signed", txHash != null);
        payload.put("txHash", txHash);

        String message = txHash == null
                ? "서명이 확인되지 않았습니다. 다시 시도해 주세요."
                : "가공 작업을 블록체인에 등록했습니다. 이제 업체가 체인에서 수락하기를 "
                        + "기다립니다 — 수락한 뒤에 소재를 인계할 수 있습니다.";
        payload.put("message", message);
        payload.put(
                "nextStep",
                "업체의 온체인 수락은 담당자가 할 수 없다. 기다리는 동안 재촉할 tool 을 찾지 "
                        + "말고, get_my_processing 으로 상태를 확인해 알린다.");
        return AgentToolResult.of(json(payload), message);
    }
}
