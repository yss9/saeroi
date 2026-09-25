package com.ssafy.upcycle.agent.tool.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.PendingActionKind;
import com.ssafy.upcycle.product.dto.ProductModelResponse;
import com.ssafy.upcycle.product.dto.ProductModelStatusRequest;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import com.ssafy.upcycle.product.service.ProductModelQueryService;
import com.ssafy.upcycle.product.service.ProductModelRegistrationService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
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
    private final ProductModelQueryService productQuery;

    public SetProductOnSaleTool(
            ObjectMapper objectMapper,
            ProductModelRegistrationService registrationService,
            ProductModelQueryService productQuery) {
        super(objectMapper);
        this.registrationService = registrationService;
        this.productQuery = productQuery;
    }

    /**
     * 재고가 없으면 카드를 띄우지 않는다. 도메인이 {@code PRODUCT_MODEL_HAS_NO_STOCK} 으로
     * 거절하는 자리다 — 생산을 확정했어도 제품에 연결하기 전에는 재고가 0 이다.
     *
     * <p>이 도구는 서버가 스스로 띄우는 카드이기도 해서, 여기서 막지 않으면 담당자가 부르지도
     * 않은 카드가 떴다가 누르는 순간 실패한다.
     */
    @Override
    public Optional<AgentToolResult> refuseBeforeProposal(AgentToolContext context, JsonNode arguments) {
        ProductModelResponse model = findModel(context, arguments);
        if (model == null || model.status() == ProductModelStatus.ON_SALE) {
            return Optional.empty();
        }
        // **0원이면 판매를 시작하지 않는다.** 판매가를 묻지 않고 등록한 제품의 판매 시작 카드가 떴고, 담당자가 여러 번
        // 취소한 끝에 승인해 0원에 판매가 시작됐다. 서버가 스스로 띄우는 카드도 이 검사를 지난다.
        if (model.price() <= 0) {
            return Optional.of(AgentToolResult.of(json(Map.of(
                    "error", "PRICE_NOT_SET",
                    "productModelId", model.productModelId(),
                    "hint", "판매가가 0원이라 판매를 시작할 수 없습니다. 담당자에게 판매가를 묻고 update_product_model 로 "
                            + "고친 뒤에 판매를 시작합니다 — 제품을 새로 등록하지 않습니다."))));
        }
        if (model.stockQuantity() > 0) {
            return Optional.empty();
        }
        return Optional.of(AgentToolResult.of(json(Map.of(
                "error", "NO_STOCK",
                "productModelId", model.productModelId(),
                "hint", "재고가 없어 판매를 시작할 수 없습니다. 생산한 개체가 이 제품에 연결돼야 재고가 "
                        + "생깁니다 — 확정한 배치를 register_product_model 로 이 제품에 연결했는지 "
                        + "확인하세요."))));
    }

    /** 이미 판매 중이면 카드를 만들지도, 실행하지도 않는다. */
    @Override
    public Optional<String> stalenessReason(AgentToolContext context, JsonNode arguments) {
        ProductModelResponse model = findModel(context, arguments);
        if (model != null && model.status() == ProductModelStatus.ON_SALE) {
            return Optional.of("이 제품은 이미 판매 중입니다. 마켓에서 구매할 수 있습니다.");
        }
        return Optional.empty();
    }

    /** 카드 제목을 서버가 쓴다. 어떤 제품을 몇 개 재고로 내놓는지가 동의하는 내용이다. */
    @Override
    public Optional<String> serverSummary(AgentToolContext context, JsonNode arguments) {
        ProductModelResponse model = findModel(context, arguments);
        if (model == null) {
            return Optional.empty();
        }
        return Optional.of("%s 판매 시작 — 재고 %d개 · %,d원".formatted(
                model.name(), model.stockQuantity(), model.price()));
    }

    private ProductModelResponse findModel(AgentToolContext context, JsonNode arguments) {
        long productModelId = requiredPositiveLong(arguments, "productModelId");
        return productQuery.findMyProductModels(context.userId()).stream()
                .filter(model -> model.productModelId() == productModelId)
                .findFirst()
                .orElse(null);
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
