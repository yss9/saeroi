package com.ssafy.upcycle.agent.tool.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.PendingActionKind;
import com.ssafy.upcycle.product.dto.ProductModelResponse;
import com.ssafy.upcycle.product.dto.ProductModelUpdateRequest;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import com.ssafy.upcycle.product.service.ProductModelQueryService;
import com.ssafy.upcycle.product.service.ProductModelRegistrationService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 등록한 제품의 이름·판매가·설명을 고친다.
 *
 * <p>이 도구가 없던 때, 모델은 0원으로 등록한 제품의 판매가를 "수정" 한다며 같은 이름의 제품을
 * 하나 더 등록했다. 150,000원짜리 새 제품은 재고가 0 이었고, 0원짜리 원래 제품이 그대로 판매됐다.
 * 고칠 때는 새로 등록하지 않고 이 도구를 쓴다.
 *
 * <p>분류와 대표 이미지는 다루지 않는다. 분류는 등록 뒤 바꿀 수 없고, 이미지는 대화로 올릴 수
 * 없어 제품 관리 화면에서 바꾼다.
 */
@Component
public class UpdateProductModelTool extends AbstractAgentTool {

    private final ProductModelRegistrationService registrationService;
    private final ProductModelQueryService productQuery;

    public UpdateProductModelTool(
            ObjectMapper objectMapper,
            ProductModelRegistrationService registrationService,
            ProductModelQueryService productQuery) {
        super(objectMapper);
        this.registrationService = registrationService;
        this.productQuery = productQuery;
    }

    /**
     * 누르면 실패할 카드는 띄우지 않는다. 없는 제품, 판매를 끝낸 제품, 바꿀 것이 없는 요청,
     * 0원 이하의 판매가다.
     */
    @Override
    public Optional<AgentToolResult> refuseBeforeProposal(AgentToolContext context, JsonNode arguments) {
        ProductModelResponse model = findModel(context, arguments);
        if (model == null) {
            return Optional.of(refusal("PRODUCT_NOT_FOUND",
                    "고칠 제품을 찾지 못했습니다. get_my_product_models 로 제품 번호를 확인하세요."));
        }
        if (model.status() == ProductModelStatus.DISCONTINUED) {
            return Optional.of(refusal("PRODUCT_DISCONTINUED",
                    "판매를 끝낸 제품이라 고칠 수 없습니다."));
        }
        Long price = priceOf(arguments);
        if (price != null && price <= 0) {
            return Optional.of(refusal("PRICE_REQUIRED",
                    "판매가는 0원보다 커야 합니다. 담당자에게 판매가를 묻고 그 금액으로 다시 부르세요."));
        }
        if (changesOf(model, arguments).isEmpty()) {
            return Optional.of(refusal("NOTHING_TO_UPDATE",
                    "지금 값과 다른 것이 없습니다. 무엇을 어떻게 바꿀지 담당자에게 확인하세요."));
        }
        return Optional.empty();
    }

    /** 카드 제목을 서버가 쓴다. 무엇이 무엇으로 바뀌는지가 동의하는 내용이다. */
    @Override
    public Optional<String> serverSummary(AgentToolContext context, JsonNode arguments) {
        ProductModelResponse model = findModel(context, arguments);
        if (model == null) {
            return Optional.empty();
        }
        List<String> changes = changesOf(model, arguments);
        if (changes.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("%s 수정 — %s".formatted(model.name(), String.join(" · ", changes)));
    }

    /** 바뀌는 것만 적는다. 지금 값과 같은 인자는 바꾸는 것이 아니다. */
    private List<String> changesOf(ProductModelResponse model, JsonNode arguments) {
        List<String> changes = new ArrayList<>();
        String name = optionalText(arguments, "name");
        if (name != null && !name.equals(model.name())) {
            changes.add("제품명 \"%s\" → \"%s\"".formatted(model.name(), name));
        }
        Long price = priceOf(arguments);
        if (price != null && price != model.price()) {
            changes.add("판매가 %,d원 → %,d원".formatted(model.price(), price));
        }
        String description = optionalText(arguments, "description");
        if (description != null && !Objects.equals(description, model.description())) {
            changes.add("설명 수정");
        }
        return changes;
    }

    /** 숫자가 아니면 없는 것으로 본다 — 사전 점검은 던지지 않는다. 실행에서 다시 읽는다. */
    private Long priceOf(JsonNode arguments) {
        try {
            return optionalLong(arguments, "price");
        } catch (RuntimeException notANumber) {
            return null;
        }
    }

    private ProductModelResponse findModel(AgentToolContext context, JsonNode arguments) {
        JsonNode id = arguments.path("productModelId");
        if (!id.canConvertToLong() || id.asLong() <= 0) {
            return null;
        }
        long productModelId = id.asLong();
        return productQuery.findMyProductModels(context.userId()).stream()
                .filter(model -> model.productModelId() == productModelId)
                .findFirst()
                .orElse(null);
    }

    private AgentToolResult refusal(String error, String hint) {
        return AgentToolResult.of(json(Map.of("error", error, "hint", hint)));
    }

    @Override
    public String name() {
        return "update_product_model";
    }

    @Override
    public String description() {
        return "등록한 제품의 이름·판매가·설명을 고친다. 바꿀 것만 넣는다. "
                + "판매가를 고칠 때 제품을 새로 등록하지 않는다 — 제품이 둘이 된다. "
                + "분류와 대표 이미지는 여기서 바꾸지 않는다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "productModelId": {"type": "integer"},
                    "name": {"type": "string", "description": "새 제품명. 바꿀 때만"},
                    "price": {"type": "integer", "description": "새 판매가(원). 담당자에게 들은 금액. 바꿀 때만"},
                    "description": {"type": "string", "description": "새 제품 설명. 바꿀 때만"},
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
        ProductModelResponse updated = registrationService.update(
                context.userId(),
                productModelId,
                new ProductModelUpdateRequest(
                        optionalText(arguments, "name"),
                        null,
                        optionalLong(arguments, "price"),
                        optionalText(arguments, "description"),
                        null));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("productModel", updated);
        String message = "'%s' 정보를 고쳤습니다 — 판매가 %,d원.".formatted(updated.name(), updated.price());
        payload.put("message", message);
        return AgentToolResult.of(json(payload), message);
    }
}
