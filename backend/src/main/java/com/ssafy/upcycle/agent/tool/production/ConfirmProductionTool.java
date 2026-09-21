package com.ssafy.upcycle.agent.tool.production;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.PendingActionKind;
import com.ssafy.upcycle.production.dto.ProductionBatchResponse;
import com.ssafy.upcycle.production.service.ProductionConfirmService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 생산을 확정한다. <b>이 여정에서 되돌릴 수 없는 유일한 지점이다.</b>
 *
 * <p>확정은 투입한 소재를 소비하고 제품 개체를 만든다. 확정된 배치는 취소할 수 없다 —
 * 소재는 이미 없어졌고 개체에는 번호가 붙었다. 그래서 모델이 담당자에게 무엇이 사라지고
 * 무엇이 생기는지 말한 뒤에 불러야 한다.
 *
 * <p><b>제품은 아직 안 붙어 있어도 된다.</b> 확정은 제품을 요구하지 않는다 — 브랜드의
 * 실제 순서가 "생산을 끝내고 → 그 결과물에 제품을 붙인다" 이고,
 * {@code assignProductModel} 이 확정 뒤 연결을 정상 순서로 다룬다. 제품이 붙기 전까지
 * 그 개체가 어느 제품의 재고로도 세지 않을 뿐이다.
 *
 * <p>필수 공정이 빠진 소재도 여기서 막힌다. 그건 {@code check_processing_plan} 이 미리
 * 알려 주므로, 확정 전에 한 번 확인하는 편이 낫다.
 */
@Component
public class ConfirmProductionTool extends AbstractAgentTool {

    private final ProductionConfirmService confirmService;

    public ConfirmProductionTool(
            ObjectMapper objectMapper, ProductionConfirmService confirmService) {
        super(objectMapper);
        this.confirmService = confirmService;
    }

    @Override
    public String name() {
        return "confirm_production";
    }

    @Override
    public String description() {
        return "생산을 확정한다. 투입한 소재가 소비되고 제품 개체가 만들어진다. "
                + "되돌릴 수 없으므로 부르기 전에 무엇이 소비되고 몇 개가 생기는지 담당자에게 말해야 한다. "
                + "만들 제품은 확정 뒤에 붙여도 된다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "batchId": {"type": "integer"},
                    "summary": {"type": "string",
                      "description": "확인 카드에 보일 한 줄 설명. 되돌릴 수 없다는 점을 담는다"}
                  },
                  "required": ["batchId", "summary"],
                  "additionalProperties": false
                }""";
    }

    @Override
    public Set<JourneyPhase> phases() {
        return Set.of(JourneyPhase.PRODUCTION);
    }

    @Override
    public boolean isWrite() {
        return true;
    }

    @Override
    public PendingActionKind pendingKind() {
        return PendingActionKind.CONFIRM;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        long batchId = requiredPositiveLong(arguments, "batchId");
        ProductionBatchResponse confirmed = confirmService.confirm(context.userId(), batchId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("batch", confirmed);
        String message = "생산을 확정했습니다. 제품 %d개가 만들어졌습니다."
                .formatted(confirmed.producedQuantity());
        payload.put("message", message);
        return AgentToolResult.of(json(payload), message);
    }
}
