package com.ssafy.upcycle.agent.tool.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.project.AgentProject;
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
import com.ssafy.upcycle.production.dto.ProductionBatchResponse;
import com.ssafy.upcycle.production.entity.ProductionBatchStatus;
import java.util.Optional;
import com.ssafy.upcycle.agent.project.AgentProjects;
import com.ssafy.upcycle.agent.service.step.ProductionChainProgress;
import com.ssafy.upcycle.product.service.ProductModelQueryService;
import org.springframework.beans.factory.ObjectProvider;
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
    private final ProductionChainProgress chainRecord;
    private final ProductModelQueryService productQuery;
    /** 늦게 꺼낸다 — 프로젝트 판정이 도구 목록을 쓰고 도구 목록이 이 도구를 품는다({@code MyProcessingTool}). */
    private final ObjectProvider<AgentProjects> projects;

    public RegisterProductModelTool(
            ObjectMapper objectMapper,
            ProductModelRegistrationService registrationService,
            ProductionBatchService batchService,
            ProductionChainProgress chainRecord,
            ProductModelQueryService productQuery,
            ObjectProvider<AgentProjects> projects) {
        super(objectMapper);
        this.registrationService = registrationService;
        this.batchService = batchService;
        this.chainRecord = chainRecord;
        this.productQuery = productQuery;
        this.projects = projects;
    }

    /** 대화에서 등록한 제품과, 등록하며 연결한 배치가 이 프로젝트의 것이다. */
    @Override
    public void claim(JsonNode arguments, JsonNode result, AgentProject.Builder project) {
        project.productModel(AgentProject.idAt(result, "productModel", "productModelId"));
        project.batch(AgentProject.idAt(result, "assignedToBatchId"));
    }

    /**
     * 연결할 배치가 제품을 받을 수 없으면 <b>카드 전에</b> 막는다. 제품 등록 카드는 사진까지 올린
     * 뒤에 누르는 카드라, 누른 뒤에 거절되면 담당자는 올린 사진과 적은 내용을 다시 해야 한다.
     *
     * <p>도메인이 배치 연결을 거절하는 경우를 옮겼다({@code ProductionBatchService.assignProductModel}):
     * 초안도 확정도 아닌 배치. 이미 제품이 붙은 확정 배치는 여기가 아니라 {@link #stalenessReason}
     * 이 "이미 했다" 로 닫는다.
     */
    @Override
    public Optional<AgentToolResult> refuseBeforeProposal(AgentToolContext context, JsonNode arguments) {
        // **판매가는 담당자에게 들은 금액이어야 한다.** 모델이 판매가를 묻지 않고 0원으로 등록했고, 그 제품의 판매
        // 시작 카드가 떠 결국 0원에 판매가 시작됐다.
        JsonNode price = arguments.get("price");
        if (price == null || !price.isIntegralNumber() || price.asLong() <= 0) {
            return Optional.of(AgentToolResult.of(json(Map.of(
                    "error", "PRICE_REQUIRED",
                    "hint", "판매가가 없습니다(0원). 담당자에게 판매가를 묻고 그 금액으로 다시 부르세요 — 지어내지 않습니다."))));
        }
        Optional<AgentToolResult> duplicate = alreadyRegisteredHere(context, arguments);
        if (duplicate.isPresent()) {
            return duplicate;
        }
        ProductionBatchResponse batch = targetBatch(context, arguments);
        // **블록체인 기록 네 번을 먼저 끝낸다.** 둘을 한꺼번에 권하자 모델이 제품 등록을 먼저 골랐고, 순서가 뒤엉켜
        // 판매 시작 카드가 기록보다 먼저 떴다.
        if (batch != null && batch.status() == ProductionBatchStatus.CONFIRMED && chainPending(batch)) {
            return Optional.of(AgentToolResult.of(json(Map.of(
                    "error", "CHAIN_RECORD_FIRST",
                    "batchId", batch.productionBatchId(),
                    "hint", "이 배치의 블록체인 기록(서명 4번)이 아직 끝나지 않았습니다. record_production_chain 으로 "
                            + "기록을 먼저 마친 뒤, 판매가와 제품 정보를 정해 등록합니다."))));
        }
        if (batch == null
                || batch.status() == ProductionBatchStatus.DRAFT
                || batch.status() == ProductionBatchStatus.CONFIRMED) {
            return Optional.empty();
        }
        return Optional.of(AgentToolResult.of(json(Map.of(
                "error", "BATCH_NOT_ASSIGNABLE",
                "batchId", batch.productionBatchId(),
                "status", batch.status(),
                "hint", "이 배치에는 제품을 연결할 수 없는 상태입니다(%s). batchId 없이 제품만 등록하거나, "
                        .formatted(batch.status())
                        + "담당자에게 배치 상태를 알리세요."))));
    }

    /** 이미 제품이 붙은 확정 배치면 새 제품 카드를 띄우지 않는다. */
    @Override
    public Optional<String> stalenessReason(AgentToolContext context, JsonNode arguments) {
        ProductionBatchResponse batch = targetBatch(context, arguments);
        if (batch != null
                && batch.status() == ProductionBatchStatus.CONFIRMED
                && batch.productModelId() != null) {
            return Optional.of("이 배치는 이미 제품 %d 에 연결돼 있습니다. 다음은 그 제품의 판매 시작입니다."
                    .formatted(batch.productModelId()));
        }
        return Optional.empty();
    }

    /** 체인을 읽다 실패하면 막지 않는다 — 판정은 도움이고, 못 읽는 환경에서 등록이 통째로 막히면 안 된다. */
    private boolean chainPending(ProductionBatchResponse batch) {
        try {
            return chainRecord.isPending(batch.chainBatchId());
        } catch (RuntimeException failure) {
            return false;
        }
    }

    /**
     * 이 대화에서 <b>이미 등록한 제품</b>을 또 등록하려는가.
     *
     * <p>제품을 고치는 도구가 없던 때, 모델은 판매가를 "수정" 한다며 같은 이름의 제품을 하나 더 등록했다 — 150,000원
     * 짜리 새 제품은 재고가 0 이었고, 0원짜리 원래 제품이 그대로 판매됐다. 고칠 때는 update_product_model 을 쓴다.
     */
    private Optional<AgentToolResult> alreadyRegisteredHere(AgentToolContext context, JsonNode arguments) {
        JsonNode name = arguments.get("name");
        if (name == null || !name.isTextual() || context.conversation() == null) {
            return Optional.empty();
        }
        try {
            AgentProject project = projects.getObject().of(context.userId(), context.conversation().getId());
            if (project.productModelIds().isEmpty()) {
                return Optional.empty();
            }
            return productQuery.findMyProductModels(context.userId()).stream()
                    .filter(model -> project.ownsProductModel(model.productModelId()))
                    .filter(model -> name.asText().strip().equals(model.name()))
                    .findFirst()
                    .map(model -> AgentToolResult.of(json(Map.of(
                            "error", "ALREADY_REGISTERED",
                            "productModelId", model.productModelId(),
                            "hint", "이 대화에서 이미 등록한 제품입니다(%s, 판매가 %,d원). 이름·판매가·설명을 고치려면 "
                                    .formatted(model.name(), model.price())
                                    + "update_product_model 을 씁니다 — 새로 등록하면 제품이 둘이 됩니다."))));
        } catch (RuntimeException failure) {
            return Optional.empty();
        }
    }

    /** 연결할 배치. batchId 가 없으면 제품만 등록하는 것이라 볼 배치가 없다. */
    private ProductionBatchResponse targetBatch(AgentToolContext context, JsonNode arguments) {
        Long batchId = optionalLong(arguments, "batchId");
        if (batchId == null) {
            return null;
        }
        return batchService.findMyBatches(context.userId()).stream()
                .filter(batch -> batch.productionBatchId().equals(batchId))
                .findFirst()
                .orElse(null);
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
                    "category": {"type": "string", "enum": [%s],
                      "description": "제품 분류. 지갑은 WALLET 이다 — 가방(BAG)으로 넣지 않는다. 만드는 제품으로 정하고 담당자에게 분류를 묻지 않는다"},
                    "subcategory": {"type": "string", "enum": [%s],
                      "description": "세부 분류. 의류(CLOTHING: TOP·OUTER·PANTS·SKIRT·DRESS)와 가방(BAG: TOTE·SHOULDER·CROSSBODY·BACKPACK)에만 있다. WALLET·ACCESSORY·HOME·ETC 는 세부 분류가 없으니 넘기지 않는다. 가방·의류일 때도 담당자에게 고르라고 묻지 말고 제품 이름과 설명으로 정한다"},
                    "price": {"type": "integer", "minimum": 0, "description": "판매가(원). 담당자에게 들은 금액 — 지어내지 않는다"},
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
