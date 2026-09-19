package com.ssafy.upcycle.agent.tool.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.PendingActionKind;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 업체가 돌려보낸 가공 결과를 받아들인다. <b>여기서 새 소재가 생긴다.</b>
 *
 * <p>승인하면 업체가 쥐고 있던 투입 소재가 소비되고, 결과물이 새 소재로 발행되어 담당자의
 * 보유 목록에 들어온다. 부모-자식이 {@code material_lineage} 로 이어져 제품 패스포트의
 * 이력이 된다. 되돌릴 수 없다 — 그래서 서명을 받는다.
 *
 * <h2>서버가 체인에 쓰지 않는다</h2>
 *
 * <p>{@code request_chain_order} 와 같다. 프론트가 온체인 승인({@code approveProcessingResult})
 * 을 서명하고, 그 영수증을 기존 API({@code POST /processing-jobs/{id}/return/receive})로
 * 붙인다 — <b>화면에서 승인하는 것과 같은 경로</b>다. 서버는 영수증을 RPC 로 확인한 뒤에야
 * 소재를 발행한다({@code ProcessingChainService#receiveReturn}).
 *
 * <p>도메인에 서명 없이 승인하는 메서드({@code ProcessingJobWorkflowService#approve})가
 * 따로 있지만 쓰지 않는다. 그쪽은 어떤 화면도 부르지 않는 경로이고, 체인 영수증을 확인하지
 * 않으며 소비하는 잠금도 다르다. 대화가 그 길로 가면 화면으로 한 승인과 대화로 한 승인이
 * 서로 다른 결과를 남긴다.
 *
 * <h2>업체가 결과를 돌려보낸 뒤에만 가능하다</h2>
 *
 * <p>{@code receiveReturn} 은 작업이 {@code RETURN_SHIPPED} 일 때만 받는다. 결과만 올라오고
 * 아직 발송 전이면 승인할 수 없다 — 물건이 오는 중인데 소유를 옮길 수는 없다.
 */
@Component
public class ApproveProcessingResultTool extends AbstractAgentTool {

    public ApproveProcessingResultTool(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String name() {
        return "approve_processing_result";
    }

    @Override
    public String description() {
        return "업체가 돌려보낸 가공 결과를 승인하도록 담당자에게 서명 카드를 띄운다. "
                + "승인하면 투입 소재가 소비되고 결과물이 새 소재로 발행되어 보유 목록에 들어온다. "
                + "되돌릴 수 없으므로 담당자가 결과와 수량을 확인했는지 먼저 묻는다. "
                + "결과가 잘못됐다면 승인하지 말고 가공 작업 화면에서 거절해야 한다고 안내한다. "
                + "업체가 결과를 발송한 뒤에만 쓸 수 있다.";
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
     * <p>{@code txHash} 를 검증하지 않는다. {@code receiveReturn} 이 영수증을 RPC 로 확인하며
     * 이미 하고 있고, 여기서 또 하면 같은 규칙이 두 곳에 생긴다.
     */
    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        long jobId = requiredPositiveLong(arguments, "jobId");
        JsonNode userResult = arguments.path("_userResult");
        String txHash = userResult.path("txHash").asText(null);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jobId", jobId);
        payload.put("signed", txHash != null);
        payload.put("txHash", txHash);

        String message = txHash == null
                ? "서명이 확인되지 않았습니다. 다시 시도해 주세요."
                : "가공 결과를 승인했습니다. 결과물이 새 소재로 발행되어 보유 목록에 들어왔습니다. "
                        + "이제 생산 단계로 넘어갈 수 있습니다.";
        payload.put("message", message);
        return AgentToolResult.of(json(payload), message);
    }
}
