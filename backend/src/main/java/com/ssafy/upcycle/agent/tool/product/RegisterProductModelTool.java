package com.ssafy.upcycle.agent.tool.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolArgumentException;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.PendingActionKind;
import com.ssafy.upcycle.product.dto.ProductModelRegisterRequest;
import com.ssafy.upcycle.product.dto.ProductModelResponse;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductSubcategory;
import com.ssafy.upcycle.product.service.ProductModelRegistrationService;
import com.ssafy.upcycle.production.dto.ProductionBatchProductModelRequest;
import com.ssafy.upcycle.production.service.ProductionBatchService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 제품을 등록한다. 등록만으로는 팔리지 않는다 — 초안 상태로 들어간다.
 *
 * <p><b>{@code batchId} 를 함께 받는 이유.</b> 생산 확정은 "무엇을 만드는가" 가 배치에 붙어
 * 있어야 한다. 제품 등록과 배치 연결을 따로 두면 담당자는 대화 중에 두 번을 승인해야 하고,
 * 그 사이에 연결을 잊으면 확정 단계에서 막힌다. 한 번에 끝낸다.
 *
 * <p>그래서 이 tool 은 생산 단계에서도 보인다. 실제 순서가 "배치를 열고 → 소재를 넣고 →
 * 몇 개가 나오는지 알고 → 그제야 제품을 정한다" 이기 때문이다.
 *
 * <p><b>대표 이미지는 모델이 넣지 않는다.</b> 채팅으로는 파일을 올릴 수 없으므로 확인
 * 카드가 업로드를 받고, 담당자가 올린 주소가 승인과 함께 {@code _userResult} 로 들어온다.
 * 모델이 그럴듯한 주소를 지어내면 마켓에 깨진 이미지가 올라가므로 인자에서 아예 뺐다.
 */
@Component
public class RegisterProductModelTool extends AbstractAgentTool {

    private final ProductModelRegistrationService registrationService;
    private final ProductionBatchService batchService;

    public RegisterProductModelTool(
            ObjectMapper objectMapper,
            ProductModelRegistrationService registrationService,
            ProductionBatchService batchService) {
        super(objectMapper);
        this.registrationService = registrationService;
        this.batchService = batchService;
    }

    @Override
    public String name() {
        return "register_product_model";
    }

    @Override
    public String description() {
        return "제품을 등록한다. 등록하면 초안 상태가 되고, 판매는 set_product_on_sale 로 따로 시작한다. "
                + "batchId 를 넘기면 그 생산 배치에 바로 연결된다 — 생산을 확정하려면 연결돼 있어야 한다. "
                + "대표 이미지는 확인 카드에서 담당자가 직접 올리므로 여기서 넘기지 않는다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "name": {"type": "string", "maxLength": 200},
                    "category": {"type": "string", "enum": [%s]},
                    "subcategory": {"type": "string", "enum": [%s]},
                    "price": {"type": "integer", "minimum": 0, "description": "판매가(원)"},
                    "description": {"type": "string", "description": "제품 설명"},
                    "batchId": {"type": "integer",
                      "description": "이 제품을 만들 생산 배치. 넘기면 바로 연결된다"},
                    "summary": {"type": "string", "description": "확인 카드에 보일 한 줄 설명"}
                  },
                  "required": ["name", "category", "price", "description", "summary"],
                  "additionalProperties": false
                }"""
                .formatted(enumValues(ProductCategory.class), enumValues(ProductSubcategory.class));
    }

    @Override
    public Set<JourneyPhase> phases() {
        return Set.of(JourneyPhase.PRODUCTION, JourneyPhase.PRODUCT);
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
        ProductModelResponse registered = registrationService.register(
                context.userId(),
                new ProductModelRegisterRequest(
                        requiredText(arguments, "name"),
                        category(arguments),
                        subcategory(arguments),
                        requiredLong(arguments, "price"),
                        requiredText(arguments, "description"),
                        uploadedThumbnailUrl(arguments)));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("productModel", registered);

        String message = "제품 '%s' 을(를) 등록했습니다.".formatted(registered.name());
        Long batchId = optionalLong(arguments, "batchId");
        if (batchId != null) {
            batchService.assignProductModel(
                    context.userId(),
                    batchId,
                    new ProductionBatchProductModelRequest(registered.productModelId()));
            payload.put("assignedToBatchId", batchId);
            message += " 생산 배치에 연결했습니다.";
        }

        payload.put("message", message);
        return AgentToolResult.of(json(payload), message);
    }

    /**
     * 확인 카드가 올린 이미지 주소. 승인과 함께 {@code _userResult} 로 들어온다.
     *
     * <p>여기서 비어 있으면 카드가 업로드 없이 승인됐다는 뜻이다. 서버가 빈 값을 거부하므로
     * 어차피 등록되지 않지만, 그때 나가는 것은 담당자가 읽을 수 없는 검증 오류다.
     */
    private String uploadedThumbnailUrl(JsonNode arguments) {
        String uploaded = optionalText(arguments.path("_userResult"), "thumbnailUrl");
        if (uploaded != null) {
            return uploaded;
        }
        throw new AgentToolArgumentException(
                "대표 이미지가 없습니다. 확인 카드에서 이미지를 올린 뒤 승인해 주세요.");
    }

    private ProductCategory category(JsonNode arguments) {
        String raw = requiredText(arguments, "category");
        try {
            return ProductCategory.valueOf(raw);
        } catch (IllegalArgumentException unknown) {
            throw new AgentToolArgumentException("알 수 없는 카테고리입니다: " + raw);
        }
    }

    private ProductSubcategory subcategory(JsonNode arguments) {
        String raw = optionalText(arguments, "subcategory");
        if (raw == null) {
            return null;
        }
        try {
            return ProductSubcategory.valueOf(raw);
        } catch (IllegalArgumentException unknown) {
            throw new AgentToolArgumentException("알 수 없는 세부 카테고리입니다: " + raw);
        }
    }
}
