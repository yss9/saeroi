package com.ssafy.upcycle.agent.tool.production;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.project.AgentProject;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.PendingActionKind;
import com.ssafy.upcycle.production.dto.ProductionBatchOpenRequest;
import com.ssafy.upcycle.production.dto.ProductionBatchResponse;
import com.ssafy.upcycle.production.service.ProductionBatchService;
import java.util.LinkedHashMap;
import java.util.Map;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.util.Optional;
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

    /**
     * 지갑 연결을 카드 전에 본다. 저장소를 읽는다 — 없으면 비어 있을 뿐 예외를 던지지 않아, 카드를
     * 만드는 트랜잭션을 망가뜨리지 않는다.
     */
    private final UserWalletRepository wallets;

    public OpenProductionBatchTool(
            ObjectMapper objectMapper, ProductionBatchService batchService, UserWalletRepository wallets) {
        super(objectMapper);
        this.batchService = batchService;
        this.wallets = wallets;
    }

    /**
     * <b>지갑이 없으면 카드 전에 막는다.</b> 배치를 열 때 확정 서명에 쓸 지갑 주소를 배치에
     * 고정하므로({@code ProductionBatchService.open}), 지갑이 없으면 승인 뒤에 거절된다. 담당자는
     * 누르고 나서야 지갑부터 연결해야 한다는 것을 알게 된다.
     */
    @Override
    public Optional<AgentToolResult> refuseBeforeProposal(AgentToolContext context, JsonNode arguments) {
        if (wallets.findByUserIdAndActiveTrue(context.userId()).isPresent()) {
            return Optional.empty();
        }
        return Optional.of(AgentToolResult.of(json(Map.of(
                "error", "WALLET_NOT_LINKED",
                "hint", "지갑이 연결돼 있지 않아 생산 배치를 열 수 없습니다. 배치에는 생산 확정 서명에 쓸 "
                        + "지갑 주소가 고정됩니다. 담당자에게 먼저 지갑을 연결하도록 안내하세요."))));
    }

    /** 대화에서 연 배치는 이 프로젝트의 생산이다. */
    @Override
    public void claim(JsonNode arguments, JsonNode result, AgentProject.Builder project) {
        project.batch(AgentProject.idAt(result, "batch", "productionBatchId"));
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
                      "description": "담당자가 나중에 알아볼 이름. 예: '2026 봄 데님 토트백 1차'. 담당자가 정하지 않았으면 만드는 제품으로 지어 제안하고, 무엇으로 지었는지 말한다 — 카드에 그대로 뜬다"},
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
    public boolean isWrite() {
        return true;
    }

    @Override
    public PendingActionKind pendingKind() {
        return PendingActionKind.CONFIRM;
    }

    /**
     * 카드 제목을 서버가 쓴다 — <b>배치 이름과 개수</b>가 담당자가 동의하는 내용이다.
     *
     * <p>모델이 쓴 요약에는 이름이 빠지곤 했다. "울 원단 5kg, 폐데님 원단 5kg으로 지갑 10개 생산" 이 카드에
     * 떴고, 실제로 열린 배치 이름 "울·데님 지갑 10개" 는 담당자가 승인하기 전까지 어디에도 없었다. 수동
     * 화면에서는 이 이름을 담당자가 직접 적는다.
     */
    @Override
    public Optional<String> serverSummary(AgentToolContext context, JsonNode arguments) {
        JsonNode name = arguments.get("batchName");
        JsonNode quantity = arguments.get("producedQuantity");
        if (name == null || !name.isTextual() || name.asText().isBlank()
                || quantity == null || !quantity.isIntegralNumber() || quantity.asLong() < 1) {
            return Optional.empty();
        }
        return Optional.of("생산 배치 열기 — \"%s\" · %,d개 생산".formatted(name.asText().strip(), quantity.asLong()));
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
