package com.ssafy.upcycle.agent.tool.production;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.PendingActionKind;
import com.ssafy.upcycle.production.dto.ProductionBatchResponse;
import com.ssafy.upcycle.production.service.ProductionBatchService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 진행하지 않을 생산 배치를 정리한다. <b>묶인 소재를 풀어 주는 것이 요점이다.</b>
 *
 * <h2>왜 필요한가 — 소재가 영구히 묶인다</h2>
 *
 * <p>배치에 소재를 투입하면 그만큼 {@code MaterialLock} 이 걸린다. 보유량은 그대로인데
 * 가용량이 줄고, <b>그 잠금에는 만료가 없다</b>({@code ProductionBatchService.link} 참고 —
 * 기한이 지나 풀리면 확정 시점에 소재가 사라진 배치가 되기 때문이다).
 *
 * <p>그래서 10kg 중 9kg 을 투입해 둔 배치를 그만두면 쓸 수 있는 양이 1kg 으로 남는다. 다른
 * 배치에 넣을 수도, 판매 글로 올릴 수도 없다 — 판매 등록이 같은 가용량 계산을 쓴다. 풀 길은
 * 원하지 않는 제품을 확정해 버리거나 DB 를 직접 고치는 것뿐이었다.
 *
 * <p>{@code ProductionBatchService.cancel} 이 잠금을 풀고 사건을 남기는 일을 이미 다 한다.
 * <b>빠져 있던 것은 대화에서 그것을 부를 방법이다</b> — 엔드포인트는 있는데 화면도 에이전트도
 * 부르지 않아 실제로는 닿을 수 없는 기능이었다.
 *
 * <h2>확정된 배치는 다루지 않는다</h2>
 *
 * <p>소재는 이미 소비됐고 개체에는 번호가 붙었다. 그걸 되돌리는 일은 취소가 아니라 별도의
 * 보정이다. 도메인이 거절하므로 여기서 따로 막지 않는다.
 *
 * <p>투입 행은 지워지지 않는다. "이런 소재를 넣으려다 그만뒀다" 도 이력이다.
 */
@Component
public class CancelProductionBatchTool extends AbstractAgentTool {

    private final ProductionBatchService batchService;

    public CancelProductionBatchTool(
            ObjectMapper objectMapper, ProductionBatchService batchService) {
        super(objectMapper);
        this.batchService = batchService;
    }

    @Override
    public String name() {
        return "cancel_production_batch";
    }

    @Override
    public String description() {
        return "진행하지 않을 생산 배치를 취소한다. 투입해 둔 소재의 잠금이 풀려 다시 쓸 수 있게 된다. "
                + "확정 전 배치만 가능하고, 확정된 배치는 취소할 수 없다. "
                + "담당자가 그 배치를 그만두겠다고 말했을 때만 부른다 — 먼저 권유하지 않는다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "batchId": {"type": "integer",
                      "description": "취소할 생산 배치 번호. get_my_production_batches 의 productionBatchId 이거나 시스템 프롬프트의 현재 상태 목록에 적힌 번호다. **카드 번호(cardId)를 쓰지 않는다**"},
                    "summary": {"type": "string",
                      "description": "확인 카드에 보일 한 줄. 어느 배치를 정리하는지와 풀리는 소재를 담는다"}
                  },
                  "required": ["batchId", "summary"],
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

    /**
     * 확인 카드다. 서명이 필요하지 않다 — 온체인에 기록된 것이 없기 때문이다.
     *
     * <p>{@code DRAFT} 배치는 체인에 올라간 적이 없다. 체인 기록은 확정 뒤에 하고, 확정된
     * 배치는 취소할 수 없다. 그래서 취소는 DB 안에서만 일어나고 체인과 어긋날 일이 없다.
     */
    @Override
    public PendingActionKind pendingKind() {
        return PendingActionKind.CONFIRM;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        long batchId = requiredPositiveLong(arguments, "batchId");
        ProductionBatchResponse canceled = batchService.cancel(context.userId(), batchId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("batch", canceled);
        String message = "생산 배치 '%s' 을(를) 취소했습니다. 투입해 둔 소재를 다시 쓸 수 있습니다."
                .formatted(canceled.batchName());
        payload.put("message", message);
        return AgentToolResult.of(json(payload), message);
    }
}
