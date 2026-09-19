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
 * 결제한 가공 작업을 체인에 올리고 소재를 업체로 보낸다.
 *
 * <h2>순서가 강제된다</h2>
 *
 * <p>발송은 온체인 등록 뒤에만 가능하다 — {@code ProcessingJobWorkflowService#ship} 이
 * {@code chainJobId} 가 없으면 거절한다. 물건이 먼저 떠나면 체인에는 아무 기록도 없는 채로
 * 소재가 남의 손에 가 있는 구간이 생기기 때문이다.
 *
 * <p>그래서 담당자가 하는 일은 서명 하나지만, 그 뒤에 등록과 발송이 이어진다. 프론트가
 * 준비({@code GET /processing-jobs/{id}/chain}) → 서명 → 연결({@code POST .../chain}) →
 * 발송({@code PATCH .../ship}) 순으로 <b>화면에서 하던 것과 같은 경로</b>를 탄다.
 *
 * <h2>서버가 체인에 쓰지 않는다</h2>
 *
 * <p>{@code request_chain_order} 와 같은 이유다. 서명은 브라우저의 개인키로만 가능하고,
 * 서버가 체인 기록을 따로 만들면 두 벌이 되어 어긋나는 순간 정본을 알 수 없다. 여기서 하는
 * 일은 무엇에 서명해야 하는지 카드로 전달하고, 끝난 사실을 대화에 남기는 것뿐이다.
 */
@Component
public class RequestCustodyTransferTool extends AbstractAgentTool {

    public RequestCustodyTransferTool(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public String name() {
        return "request_custody_transfer";
    }

    @Override
    public String description() {
        return "가공비 결제가 끝난 작업의 소재를 업체로 보내도록 담당자에게 서명 카드를 띄운다. "
                + "서명하면 작업이 블록체인에 기록되고 이어서 발송 처리된다. "
                + "부른 뒤에는 결과를 기다린다 — 서명이 끝났다고 가정하지 않는다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "jobId": {"type": "integer", "description": "결제가 끝난 가공 작업 id"},
                    "carrier": {"type": "string", "description": "택배사. 담당자가 말했으면 넣는다"},
                    "trackingNumber": {"type": "string", "description": "송장번호. 선택"},
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
     * 담당자가 서명과 발송을 끝낸 뒤에만 불린다.
     *
     * <p>{@code txHash} 를 검증하지 않는다. chain-link API 가 영수증을 RPC 로 확인하며 이미
     * 하고 있고, 여기서 또 하면 같은 규칙이 두 곳에 생긴다.
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
                : "작업이 블록체인에 기록되고 소재를 발송했습니다. 업체가 수령하면 가공이 시작됩니다.";
        payload.put("message", message);
        return AgentToolResult.of(json(payload), message);
    }
}
