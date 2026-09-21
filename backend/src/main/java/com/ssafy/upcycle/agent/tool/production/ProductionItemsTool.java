package com.ssafy.upcycle.agent.tool.production;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.production.dto.ProductionItemResponse;
import com.ssafy.upcycle.production.service.ProductionBatchService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 생산으로 만들어진 제품 개체와 그 QR 주소.
 *
 * <p>개체는 확정이 만든다. 확정 전 배치는 빈 목록이고 그것은 오류가 아니다.
 *
 * <p><b>QR 이미지는 돌려주지 않는다.</b> 이 tool 의 결과는 모델에게 가는 글자이고, 이미지는
 * 거기 담을 수 없다. 대신 개체마다 {@code qrUrl} 을 준다 — 소비자가 찍었을 때 열리는 바로
 * 그 주소다. 라벨로 뽑는 것은 생산 화면의 QR 목록이 맡으므로, 모델은 어디서 인쇄하는지를
 * 안내하면 된다.
 *
 * <p>개체가 많을 수 있어 <b>상한을 둔다.</b> 스무 개를 넘기면 앞의 몇 개와 전체 개수만
 * 준다 — 전부 넣어 봐야 입력 토큰만 늘고, 담당자가 대화창에서 200개의 QR 주소를 하나씩
 * 읽을 일은 없다.
 */
@Component
public class ProductionItemsTool extends AbstractAgentTool {

    /** 대화에 실어 보낼 개체 수. 넘으면 개수만 알린다. */
    private static final int MAX_ITEMS = 20;

    private final ProductionBatchService batchService;

    public ProductionItemsTool(ObjectMapper objectMapper, ProductionBatchService batchService) {
        super(objectMapper);
        this.batchService = batchService;
    }

    @Override
    public String name() {
        return "get_production_items";
    }

    @Override
    public String description() {
        return "생산으로 만들어진 제품 개체와 QR 주소를 본다. 개체는 생산 확정이 만들며, "
                + "확정 전에는 비어 있다. 담당자가 QR 이나 개체 수를 물으면 부른다. "
                + "QR 이미지 인쇄는 생산 화면의 QR 목록에서 한다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "batchId": {"type": "integer"}
                  },
                  "required": ["batchId"],
                  "additionalProperties": false
                }""";
    }

    /**
     * 제품 등록 단계에서만 보인다.
     *
     * <p>개체는 확정이 만들고, 확정 직후 몇 개가 나왔는지는 {@code confirm_production} 의
     * 응답이 이미 알려 준다. QR 과 개체 목록이 필요해지는 것은 제품을 올릴 때부터다.
     * 생산 단계에 함께 두면 그 단계의 tool 이 열둘을 넘어, 모델이 지금 할 일과 상관없는
     * 것을 부르기 시작한다.
     */
    @Override
    public Set<JourneyPhase> phases() {
        return Set.of(JourneyPhase.PRODUCT);
    }

    @Override
    public boolean isWrite() {
        return false;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        long batchId = requiredPositiveLong(arguments, "batchId");
        List<ProductionItemResponse> items = batchService.findItems(context.userId(), batchId);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ProductionItemResponse item : items.stream().limit(MAX_ITEMS).toList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productItemId", item.productItemId());
            row.put("serialNumber", item.serialNumber());
            row.put("status", item.status());
            row.put("qrUrl", item.qrUrl());
            rows.add(row);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("batchId", batchId);
        payload.put("totalItems", items.size());
        payload.put("items", rows);
        if (items.size() > MAX_ITEMS) {
            payload.put(
                    "truncated",
                    "개체가 %d개라 앞의 %d개만 보여 줍니다. 전체 목록과 라벨 인쇄는 생산 화면에서 합니다."
                            .formatted(items.size(), MAX_ITEMS));
        }
        if (items.isEmpty()) {
            payload.put(
                    "hint",
                    "개체가 없습니다. 생산을 확정하지 않았다면 confirm_production 이 먼저입니다 — "
                            + "확정이 개체를 만듭니다.");
        }
        return AgentToolResult.of(json(payload));
    }
}
