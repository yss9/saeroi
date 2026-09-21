package com.ssafy.upcycle.agent.tool.production;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.production.dto.ProductionBatchResponse;
import com.ssafy.upcycle.production.service.ProductionBatchService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 내 생산 배치 목록.
 *
 * <p>생산은 한 번에 끝나지 않는다 — 배치를 열고, 소재를 넣고, 제품을 붙이고, 확정한다. 그
 * 사이에 대화가 끊기거나 담당자가 다른 일을 하다 돌아온다. 그래서 모델이 "지금 어디까지
 * 됐는지" 를 먼저 볼 수 있어야 한다. 기억에 의존하면 이미 넣은 소재를 또 넣으려 한다.
 *
 * <p>제품 등록 단계에서도 보인다. 무엇을 만들었는지 알아야 제품을 등록할 수 있고, 어느
 * 배치에 붙일지도 여기서 고른다.
 */
@Component
public class MyProductionBatchesTool extends AbstractAgentTool {

    private final ProductionBatchService batchService;

    public MyProductionBatchesTool(ObjectMapper objectMapper, ProductionBatchService batchService) {
        super(objectMapper);
        this.batchService = batchService;
    }

    @Override
    public String name() {
        return "get_my_production_batches";
    }

    @Override
    public String description() {
        return "내 생산 배치 목록을 본다. 배치 상태, 투입한 소재 총량, 붙어 있는 제품을 확인할 수 있다. "
                + "생산을 이어서 진행하기 전에 먼저 불러 지금 상태를 확인한다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {"type": "object", "properties": {}, "additionalProperties": false}""";
    }

    @Override
    public Set<JourneyPhase> phases() {
        return Set.of(JourneyPhase.PRODUCTION, JourneyPhase.PRODUCT);
    }

    @Override
    public boolean isWrite() {
        return false;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        List<ProductionBatchResponse> batches = batchService.findMyBatches(context.userId());
        return AgentToolResult.of(json(Map.of("batches", batches, "count", batches.size())));
    }
}
