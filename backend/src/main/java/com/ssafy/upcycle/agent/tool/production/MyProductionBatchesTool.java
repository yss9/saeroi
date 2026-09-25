package com.ssafy.upcycle.agent.tool.production;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.project.AgentProject;
import com.ssafy.upcycle.agent.project.AgentProjects;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.production.dto.ProductionBatchResponse;
import com.ssafy.upcycle.production.service.ProductionBatchService;
import java.util.List;
import java.util.Map;
import com.ssafy.upcycle.common.unit.Quantities;
import com.ssafy.upcycle.production.dto.ProductionInputResponse;
import org.springframework.beans.factory.ObjectProvider;
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
    /** 늦게 꺼낸다 — 프로젝트 판정이 도구 목록을 쓰고 도구 목록이 이 도구를 품는다({@code MyProcessingTool}). */
    private final ObjectProvider<AgentProjects> projects;

    public MyProductionBatchesTool(
            ObjectMapper objectMapper, ProductionBatchService batchService, ObjectProvider<AgentProjects> projects) {
        super(objectMapper);
        this.batchService = batchService;
        this.projects = projects;
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
    public boolean isWrite() {
        return false;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        List<ProductionBatchResponse> batches = batchService.findMyBatches(context.userId());
        AgentProject project = projectOf(context);
        // 이 대화의 배치를 앞에 둔다. 지난 제품의 배치가 앞에 있으면 모델이 그것부터 말한다.
        List<Map<String, Object>> rows = batches.stream()
                .map(batch -> withText(batch, project.ownsBatch(batch.productionBatchId())))
                .sorted((a, b) -> Boolean.compare((boolean) b.get("thisProject"), (boolean) a.get("thisProject")))
                .toList();
        return AgentToolResult.of(json(Map.of(
                "batches", rows,
                "count", batches.size(),
                // 확정 전 배치의 totalUsedQuantity·upcycleRatio 는 0 이다(확정 때 한 번 계산한다).
                // 그것을 보고 "투입한 소재가 없다" 고 읽은 적이 있어, 투입량은 따로 준다.
                "hint", "투입한 양은 inputTotalText 와 inputs 의 inputText 를 그대로 옮긴다. "
                        + "totalUsedQuantity 와 upcycleRatio 는 확정 전에는 0 이니 투입 여부를 그것으로 판단하지 않는다. "
                        + "thisProject 가 false 인 배치는 지난 제품의 것이다 — 담당자가 묻지 않았으면 꺼내지 않는다.")));
    }

    /** 이 대화의 프로젝트. 판정이 실패해도 목록은 돌려준다 — 전부 이 대화의 것이 아닌 것으로 본다. */
    private AgentProject projectOf(AgentToolContext context) {
        try {
            Long conversationId = context.conversation() == null ? null : context.conversation().getId();
            return conversationId == null
                    ? AgentProject.empty()
                    : projects.getObject().of(context.userId(), conversationId);
        } catch (RuntimeException failure) {
            return AgentProject.empty();
        }
    }

    private Map<String, Object> withText(ProductionBatchResponse batch, boolean thisProject) {
        Map<String, Object> row = fields(batch);
        row.put("thisProject", thisProject);
        List<ProductionInputResponse> inputs = batch.inputs() == null ? List.of() : batch.inputs();
        row.put("inputs", inputs.stream().map(this::inputWithText).toList());
        row.put("inputTotalText", Quantities.text(
                inputs.stream().mapToLong(ProductionInputResponse::inputQuantity).sum()));
        return row;
    }

    private Map<String, Object> inputWithText(ProductionInputResponse input) {
        Map<String, Object> row = fields(input);
        row.put("inputText", Quantities.text(input.inputQuantity()));
        row.put("productUsedText", Quantities.text(input.productUsedQuantity()));
        row.put("reusableScrapText", Quantities.text(input.reusableScrapQuantity()));
        row.put("wasteText", Quantities.text(input.wasteQuantity()));
        return row;
    }
}
