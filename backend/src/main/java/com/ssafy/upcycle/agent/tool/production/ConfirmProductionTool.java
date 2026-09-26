package com.ssafy.upcycle.agent.tool.production;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.PendingActionKind;
import com.ssafy.upcycle.common.unit.Quantities;
import com.ssafy.upcycle.production.dto.ProductionBatchResponse;
import com.ssafy.upcycle.production.dto.ProductionInputResponse;
import com.ssafy.upcycle.production.entity.ProductionBatchStatus;
import com.ssafy.upcycle.production.service.ProductionBatchService;
import com.ssafy.upcycle.production.service.ProductionConfirmService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
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
    private final ProductionBatchService batchService;

    public ConfirmProductionTool(
            ObjectMapper objectMapper,
            ProductionConfirmService confirmService,
            ProductionBatchService batchService) {
        super(objectMapper);
        this.confirmService = confirmService;
        this.batchService = batchService;
    }

    /**
     * <b>되돌릴 수 없는 작업이라 카드 전에 본다.</b> 확정할 수 없는 배치에 카드를 띄우면
     * 담당자는 "되돌릴 수 없습니다" 라는 경고까지 읽고 누른 뒤에야 실패를 본다.
     *
     * <p>도메인이 거절하는 경우를 그대로 옮겼다({@code ProductionConfirmService.confirm}).
     * 이미 확정된 배치는 여기가 아니라 {@link #stalenessReason} 이 "이미 했다" 로 닫는다.
     */
    @Override
    public Optional<AgentToolResult> refuseBeforeProposal(AgentToolContext context, JsonNode arguments) {
        ProductionBatchResponse batch = findBatch(context, arguments);
        if (batch == null || batch.status() == ProductionBatchStatus.CONFIRMED) {
            return Optional.empty();
        }
        if (batch.status() != ProductionBatchStatus.DRAFT) {
            return Optional.of(AgentToolResult.of(json(Map.of(
                    "error", "BATCH_NOT_CONFIRMABLE",
                    "batchId", batch.productionBatchId(),
                    "status", batch.status(),
                    "hint", "이 배치는 확정할 수 있는 상태가 아닙니다(%s). 취소됐거나 온체인 기록 중인 "
                            .formatted(batch.status())
                            + "배치입니다. 담당자에게 상태를 알리고, 새로 만들려면 open_production_batch 로 "
                            + "배치를 여세요."))));
        }
        if (batch.inputs() == null || batch.inputs().isEmpty()) {
            return Optional.of(AgentToolResult.of(json(Map.of(
                    "error", "NO_INPUT",
                    "batchId", batch.productionBatchId(),
                    "hint", "아직 투입한 소재가 없어 확정할 수 없습니다. 먼저 add_production_inputs 로 "
                            + "어떤 소재를 얼마나 넣었는지 적어야 합니다."))));
        }
        return Optional.empty();
    }

    /** 이미 확정된 배치면 카드를 만들지도, 실행하지도 않는다. */
    @Override
    public Optional<String> stalenessReason(AgentToolContext context, JsonNode arguments) {
        ProductionBatchResponse batch = findBatch(context, arguments);
        if (batch != null && batch.status() == ProductionBatchStatus.CONFIRMED) {
            return Optional.of("이 배치는 이미 확정됐습니다. 다음은 블록체인 기록과 제품 연결입니다.");
        }
        return Optional.empty();
    }

    /**
     * 카드 제목을 서버가 쓴다. 되돌릴 수 없는 작업이라 무엇이 소비되고 몇 개가 생기는지가
     * 정확해야 한다.
     */
    @Override
    public Optional<String> serverSummary(AgentToolContext context, JsonNode arguments) {
        ProductionBatchResponse batch = findBatch(context, arguments);
        if (batch == null) {
            return Optional.empty();
        }
        long consumed = batch.inputs() == null ? 0 : batch.inputs().stream()
                .mapToLong(ProductionInputResponse::inputQuantity).sum();
        return Optional.of("%s 생산 확정 — 제품 %d개 · 소재 %s 소비 (되돌릴 수 없음)".formatted(
                batch.batchName(), batch.producedQuantity(), Quantities.text(consumed)));
    }

    /**
     * 목록으로 찾는다. 단건 조회 서비스는 없는 번호에 예외를 던지는데, 사전 점검에서 그 예외가
     * 나면 바깥 트랜잭션이 rollback-only 가 될 수 있다({@code AgentStepCardStore} 참고).
     */
    private ProductionBatchResponse findBatch(AgentToolContext context, JsonNode arguments) {
        long batchId = requiredPositiveLong(arguments, "batchId");
        return batchService.findMyBatches(context.userId()).stream()
                .filter(batch -> batch.productionBatchId() == batchId)
                .findFirst()
                .orElse(null);
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
                    "batchId": {"type": "integer",
                      "description": "생산 배치 번호. get_my_production_batches 의 productionBatchId 이거나 시스템 프롬프트의 현재 상태 목록에 적힌 번호다. **카드 번호(cardId)를 쓰지 않는다** — 실제로 그렇게 넘겨 배치를 찾지 못한 일이 있었다"},
                    "summary": {"type": "string",
                      "description": "확인 카드에 보일 한 줄 설명. 되돌릴 수 없다는 점을 담는다"}
                  },
                  "required": ["batchId", "summary"],
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
