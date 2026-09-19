package com.ssafy.upcycle.agent.tool.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolArgumentException;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.PendingActionKind;
import com.ssafy.upcycle.processing.dto.ProcessingJobResponse;
import com.ssafy.upcycle.processing.dto.ProcessingSignedCommandRequest;
import com.ssafy.upcycle.processing.service.ProcessingJobWorkflowService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 업체가 올린 가공 결과를 승인한다. <b>여기서 새 소재가 생긴다.</b>
 *
 * <p>승인하는 순간 투입 소재가 소비되고, 결과물이 새 소재로 발행되어 담당자의 보유 목록에
 * 들어온다. 부모-자식이 {@code material_lineage} 로 이어져 제품 패스포트의 이력이 된다.
 * 되돌릴 수 없다 — 그래서 서명을 받는다.
 *
 * <p><b>결과가 마음에 들지 않으면 승인하지 않는다.</b> 거절은 별도 경로(기존 화면)이고
 * 반송 절차가 따라붙는다. 이 tool 은 승인만 한다 — 대화에서 실수로 반송까지 일어나면
 * 되돌리는 비용이 크다.
 *
 * <p>서명 흐름은 프론트가 기존 화면과 같은 경로로 한다: 챌린지 발급
 * ({@code POST /processing-jobs/{id}/signature-challenge}, action=APPROVE) → 서명 →
 * 그 결과를 이 카드의 승인 결과로 돌려준다.
 */
@Component
public class ApproveProcessingResultTool extends AbstractAgentTool {

    private final ProcessingJobWorkflowService workflow;

    public ApproveProcessingResultTool(
            ObjectMapper objectMapper, ProcessingJobWorkflowService workflow) {
        super(objectMapper);
        this.workflow = workflow;
    }

    @Override
    public String name() {
        return "approve_processing_result";
    }

    @Override
    public String description() {
        return "업체가 올린 가공 결과를 승인한다. 승인하면 투입 소재가 소비되고 결과물이 "
                + "새 소재로 발행되어 보유 목록에 들어온다. 되돌릴 수 없으므로 담당자가 결과를 "
                + "확인했는지 먼저 묻는다. 결과가 잘못됐다면 승인하지 말고 화면에서 거절해야 "
                + "한다고 안내한다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "jobId": {"type": "integer"},
                    "summary": {"type": "string",
                      "description": "담당자가 무엇에 서명하는지 한 줄로. 카드에 그대로 보인다"}
                  },
                  "required": ["jobId", "summary"],
                  "additionalProperties": false
                }""";
    }

    @Override
    public Set<JourneyPhase> phases() {
        return Set.of(JourneyPhase.PROCESSING);
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
     * 담당자가 서명을 끝낸 뒤에만 불린다.
     *
     * <p>서명 검증은 {@code ProcessingSignatureService} 가 한다. 여기서는 챌린지와 서명을
     * 도메인에 그대로 넘긴다 — 검증 규칙이 두 곳에 생기면 한쪽만 바뀔 때 구멍이 난다.
     */
    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        long jobId = requiredPositiveLong(arguments, "jobId");
        JsonNode userResult = arguments.path("_userResult");

        Long challengeId = userResult.path("challengeId").isNumber()
                ? userResult.path("challengeId").asLong()
                : null;
        String signature = userResult.path("signature").asText(null);
        if (challengeId == null || signature == null || signature.isBlank()) {
            throw new AgentToolArgumentException(
                    "서명 결과(challengeId, signature)가 없습니다. 서명을 다시 진행해야 합니다.");
        }

        ProcessingJobResponse job = workflow.approve(
                context.userId(), jobId, new ProcessingSignedCommandRequest(challengeId, signature));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jobId", jobId);
        payload.put("job", job);

        String message = "가공 결과를 승인했습니다. 결과물이 새 소재로 발행되어 보유 목록에 들어왔습니다. "
                + "이제 생산 단계로 넘어갈 수 있습니다.";
        payload.put("message", message);
        return AgentToolResult.of(json(payload), message);
    }
}
