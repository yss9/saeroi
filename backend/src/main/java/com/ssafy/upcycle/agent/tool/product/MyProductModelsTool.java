package com.ssafy.upcycle.agent.tool.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.product.dto.ProductModelResponse;
import com.ssafy.upcycle.product.service.ProductModelQueryService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 내가 등록한 제품 목록. 판매 전 초안까지 보인다.
 *
 * <p>같은 제품을 다시 생산하는 경우가 흔하다. 그때 제품을 또 등록하면 마켓에 같은 이름이
 * 둘 생기고, 재고가 둘로 나뉜다. 등록 전에 이미 있는지 확인하라고 이 tool 을 둔다.
 */
@Component
public class MyProductModelsTool extends AbstractAgentTool {

    private final ProductModelQueryService queryService;

    public MyProductModelsTool(ObjectMapper objectMapper, ProductModelQueryService queryService) {
        super(objectMapper);
        this.queryService = queryService;
    }

    @Override
    public String name() {
        return "get_my_product_models";
    }

    @Override
    public String description() {
        return "내가 등록한 제품 목록을 본다. 판매 전 초안도 포함된다. "
                + "새로 등록하기 전에 같은 제품이 이미 있는지 확인한다.";
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
        List<ProductModelResponse> models = queryService.findMyProductModels(context.userId());
        return AgentToolResult.of(json(Map.of("productModels", models, "count", models.size())));
    }
}
