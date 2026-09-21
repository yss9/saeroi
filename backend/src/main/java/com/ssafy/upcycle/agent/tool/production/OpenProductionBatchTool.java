package com.ssafy.upcycle.agent.tool.production;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.PendingActionKind;
import com.ssafy.upcycle.production.dto.ProductionBatchOpenRequest;
import com.ssafy.upcycle.production.dto.ProductionBatchResponse;
import com.ssafy.upcycle.production.service.ProductionBatchService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 생산 배치를 연다. 여기서부터 "무엇을 몇 개 만드는가" 가 기록된다.
 *
 * <p><b>제품은 아직 없어도 된다.</b> 실제 순서가 "배치를 열고 → 소재를 넣어 보고 → 몇 개가
 * 나올지 알게 되고 → 제품을 정한다" 이기 때문이다. 소재도 고르기 전에 제품명을 확정하라고
 * 하면 담당자는 아직 모르는 것을 지어내야 한다. 다만 <b>확정 전에는 반드시 붙어야 한다</b> —
 * {@code register_product_model} 이 {@code batchId} 를 받아 그 자리에서 붙여 준다.
 *
 * <p>배치를 여는 것 자체는 소재를 소비하지 않는다. 되돌릴 수 있고, 취소도 된다. 되돌릴 수
 * 없는 지점은 {@code confirm_production} 하나뿐이다.
 */
@Component
public class OpenProductionBatchTool extends AbstractAgentTool {

    private final ProductionBatchService batchService;

    public OpenProductionBatchTool(ObjectMapper objectMapper, ProductionBatchService batchService) {
        super(objectMapper);
        this.batchService = batchService;
    }

    @Override
    public String name() {
        return "open_production_batch";
    }

    @Override
    public String description() {
        return "생산 배치를 연다. 몇 개를 만들지와 배치 이름이 필요하다. "
                + "만들 제품(productModelId)은 지금 없어도 되고, 확정 전까지 붙이면 된다. "
                + "배치를 여는 것만으로는 소재가 소비되지 않는다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "batchName": {"type": "string", "maxLength": 200,
                      "description": "담당자가 나중에 알아볼 이름. 예: '2026 봄 데님 토트백 1차'"},
                    "producedQuantity": {"type": "integer", "minimum": 1,
                      "description": "이 배치에서 만들 개수"},
                    "productModelId": {"type": "integer",
                      "description": "이미 등록된 제품이 있으면 그 id. 없으면 넣지 않는다"},
                    "summary": {"type": "string", "description": "확인 카드에 보일 한 줄 설명"}
                  },
                  "required": ["batchName", "producedQuantity", "summary"],
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
        String batchName = requiredText(arguments, "batchName");
        long producedQuantity = requiredPositiveLong(arguments, "producedQuantity");
        Long productModelId = optionalLong(arguments, "productModelId");

        ProductionBatchResponse opened = batchService.open(
                context.userId(),
                new ProductionBatchOpenRequest(
                        batchName, productModelId, Math.toIntExact(producedQuantity)));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("batch", opened);
        String message = "생산 배치 '%s' 을(를) 열었습니다. 이제 투입할 소재를 정합니다."
                .formatted(opened.batchName());
        payload.put("message", message);
        return AgentToolResult.of(json(payload), message);
    }
}
