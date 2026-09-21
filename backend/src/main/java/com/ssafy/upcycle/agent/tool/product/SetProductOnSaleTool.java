package com.ssafy.upcycle.agent.tool.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.PendingActionKind;
import com.ssafy.upcycle.product.dto.ProductModelResponse;
import com.ssafy.upcycle.product.dto.ProductModelStatusRequest;
import com.ssafy.upcycle.product.service.ProductModelRegistrationService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 제품을 마켓에 올린다. 여정의 마지막 쓰기다.
 *
 * <p>재고가 없으면 서버가 막는다 — 개체가 없는 제품을 올리면 들어온 사람이 살 수 없다.
 * 재고는 생산 확정이 만들므로, 확정 전에 판매를 시작할 수는 없다.
 *
 * <p>판매 중단({@code DISCONTINUE})은 여기서 다루지 않는다. 되돌릴 수 없는 데다 여정의
 * 일부도 아니고, 대화 중에 실수로 부를 이유가 없는 행위다. 제품 관리 화면에서 한다.
 */
@Component
public class SetProductOnSaleTool extends AbstractAgentTool {

    private final ProductModelRegistrationService registrationService;

    public SetProductOnSaleTool(
            ObjectMapper objectMapper, ProductModelRegistrationService registrationService) {
        super(objectMapper);
        this.registrationService = registrationService;
    }

    @Override
    public String name() {
        return "set_product_on_sale";
    }

    @Override
    public String description() {
        return "등록한 제품의 판매를 시작한다. 생산이 확정돼 재고가 있어야 한다. "
                + "판매가 시작되면 마켓에서 누구나 볼 수 있다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "productModelId": {"type": "integer"},
                    "summary": {"type": "string", "description": "확인 카드에 보일 한 줄 설명"}
                  },
                  "required": ["productModelId", "summary"],
                  "additionalProperties": false
                }""";
    }

    @Override
    public Set<JourneyPhase> phases() {
        return Set.of(JourneyPhase.PRODUCT);
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
        long productModelId = requiredPositiveLong(arguments, "productModelId");

        ProductModelResponse updated = registrationService.changeStatus(
                context.userId(),
                productModelId,
                new ProductModelStatusRequest(ProductModelStatusRequest.Action.ON_SALE));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("productModel", updated);
        String message = "'%s' 판매를 시작했습니다. 이제 마켓에서 볼 수 있습니다."
                .formatted(updated.name());
        payload.put("message", message);
        return AgentToolResult.of(json(payload), message);
    }
}
