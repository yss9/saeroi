package com.ssafy.upcycle.agent.tool.production;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolArgumentException;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.PendingActionKind;
import com.ssafy.upcycle.production.dto.ProductionBatchResponse;
import com.ssafy.upcycle.production.dto.ProductionInputRequest;
import com.ssafy.upcycle.production.dto.ProductionInputsRequest;
import com.ssafy.upcycle.production.service.ProductionBatchService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 배치에 소재를 투입한다. 얼마를 넣고, 그중 얼마가 제품이 되고 얼마가 남고 버려지는지 적는다.
 *
 * <p><b>왜 넷으로 쪼개 받는가.</b> 업사이클 비율은 이 내역에서 나온다. "10kg 넣었다" 만으로는
 * 제품에 실제로 들어간 양을 알 수 없고, 그러면 제품에 붙는 업사이클 비율이 실제보다 커진다.
 * 자투리와 폐기를 따로 적어야 숫자가 사실이 된다.
 *
 * <p>합계가 맞지 않으면 <b>확인 카드를 띄우기 전에</b> 돌려세운다. 엔티티가 이 규칙을
 * {@code IllegalArgumentException} 으로 막는데, 그건 담당자가 승인 버튼을 누른 뒤에 터진다.
 * 그 자리에서는 무엇이 틀렸는지도 알려 주지 못한다.
 */
@Component
public class AddProductionInputsTool extends AbstractAgentTool {

    private final ProductionBatchService batchService;

    public AddProductionInputsTool(ObjectMapper objectMapper, ProductionBatchService batchService) {
        super(objectMapper);
        this.batchService = batchService;
    }

    @Override
    public String name() {
        return "add_production_inputs";
    }

    @Override
    public String description() {
        return "생산 배치에 소재를 투입한다. 소재마다 투입량과 그 내역(제품 사용/재사용 자투리/폐기)을 "
                + "적어야 하고 셋의 합이 투입량과 같아야 한다. 내역을 모르면 담당자에게 물어본다 — "
                + "임의로 나누면 제품의 업사이클 비율이 사실과 달라진다. "
                + "투입 가능한 양은 get_my_materials 로 확인한다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "batchId": {"type": "integer"},
                    "inputs": {
                      "type": "array", "minItems": 1, "maxItems": 20,
                      "items": {
                        "type": "object",
                        "properties": {
                          "materialId": {"type": "integer"},
                          "inputQuantity": {"type": "integer", "minimum": 1,
                            "description": "이 소재에서 꺼내 쓰는 총량(gram)"},
                          "productUsedQuantity": {"type": "integer", "minimum": 0,
                            "description": "그중 실제로 제품이 된 양"},
                          "reusableScrapQuantity": {"type": "integer", "minimum": 0,
                            "description": "다시 쓸 수 있는 자투리"},
                          "wasteQuantity": {"type": "integer", "minimum": 0,
                            "description": "버리는 양"},
                          "wasteReason": {"type": "string", "maxLength": 200}
                        },
                        "required": ["materialId", "inputQuantity", "productUsedQuantity",
                                     "reusableScrapQuantity", "wasteQuantity"],
                        "additionalProperties": false
                      }
                    },
                    "summary": {"type": "string", "description": "확인 카드에 보일 한 줄 설명"}
                  },
                  "required": ["batchId", "inputs", "summary"],
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

    /** 합계가 어긋난 항목을 카드 이전에 돌려준다. 어느 소재의 무엇이 틀렸는지 함께 말한다. */
    @Override
    public Optional<AgentToolResult> refuseBeforeProposal(
            AgentToolContext context, JsonNode arguments) {
        List<Map<String, Object>> broken = new ArrayList<>();
        for (JsonNode input : inputsNode(arguments)) {
            long total = input.path("inputQuantity").asLong();
            long used = input.path("productUsedQuantity").asLong();
            long scrap = input.path("reusableScrapQuantity").asLong();
            long waste = input.path("wasteQuantity").asLong();
            if (total != used + scrap + waste) {
                broken.add(Map.of(
                        "materialId", input.path("materialId").asLong(),
                        "inputQuantity", total,
                        "sumOfBreakdown", used + scrap + waste));
            }
        }
        if (broken.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(AgentToolResult.of(json(Map.of(
                "error", "INPUT_BREAKDOWN_MISMATCH",
                "mismatched", broken,
                "hint", "투입량은 제품 사용 + 재사용 자투리 + 폐기 의 합과 같아야 합니다. "
                        + "담당자에게 실제 내역을 물어보고 다시 부르세요. 숫자를 맞추려고 "
                        + "임의로 배분하지 마세요 — 업사이클 비율이 사실과 달라집니다."))));
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        long batchId = requiredPositiveLong(arguments, "batchId");

        List<ProductionInputRequest> inputs = new ArrayList<>();
        for (JsonNode input : inputsNode(arguments)) {
            inputs.add(new ProductionInputRequest(
                    input.path("materialId").asLong(),
                    input.path("inputQuantity").asLong(),
                    input.path("productUsedQuantity").asLong(),
                    input.path("reusableScrapQuantity").asLong(),
                    input.path("wasteQuantity").asLong(),
                    input.hasNonNull("wasteReason") ? input.get("wasteReason").asText() : null));
        }

        ProductionBatchResponse updated =
                batchService.addInputs(context.userId(), batchId, new ProductionInputsRequest(inputs));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("batch", updated);
        String message = "소재 %d건을 투입했습니다. 현재 업사이클 비율은 %s 입니다."
                .formatted(inputs.size(), updated.upcycleRatio());
        payload.put("message", message);
        return AgentToolResult.of(json(payload), message);
    }

    private JsonNode inputsNode(JsonNode arguments) {
        JsonNode inputs = arguments.get("inputs");
        if (inputs == null || !inputs.isArray() || inputs.isEmpty()) {
            throw new AgentToolArgumentException("inputs 가 필요합니다.");
        }
        return inputs;
    }
}
