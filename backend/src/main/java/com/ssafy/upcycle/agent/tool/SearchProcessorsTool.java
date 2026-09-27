package com.ssafy.upcycle.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.service.CompanyNames;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.processing.dto.ProcessorSearchResponse;
import com.ssafy.upcycle.processing.entity.ProcessType;
import com.ssafy.upcycle.processing.service.ProcessorSearchService;
import java.util.ArrayList;
import java.util.List;
import com.ssafy.upcycle.common.unit.Quantities;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 소재 종류·수량·공정으로 가공업체를 찾는다.
 *
 * <p>기존 {@link ProcessorSearchService} 를 그대로 부른다. 검색 규칙(능력치 범위, 리드타임
 * 보유 여부, ACTIVE 상태)은 거기 있고 여기서 재현하지 않는다 — 화면 검색과 대화 검색이
 * 다른 결과를 내면 담당자가 어느 쪽을 믿어야 할지 알 수 없다.
 */
@Component
public class SearchProcessorsTool extends AbstractAgentTool {

    private static final int DEFAULT_SIZE = 10;

    private final ProcessorSearchService processorSearch;
    /** 검색이 돌려주는 이름은 대표자 이름이라 여러 업체가 "홍길동" 으로 보였다. 업체명으로 바꾼다. */
    private final CompanyNames companyNames;

    public SearchProcessorsTool(
            ObjectMapper objectMapper, ProcessorSearchService processorSearch, CompanyNames companyNames) {
        super(objectMapper);
        this.processorSearch = processorSearch;
        this.companyNames = companyNames;
    }

    @Override
    public String name() {
        return "search_processors";
    }

    @Override
    public String description() {
        return "소재 종류·수량(gram)·필요한 가공 공정으로 가공업체를 찾는다. "
                + "결과에는 업체별 처리 가능 수량 범위와 공정별 예상 소요일이 함께 온다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "materialType": {"type": "string", "enum": [%s],
                      "description": "실제 취급 섬유 7종 + 기타. 가죽·금속·부자재처럼 7종에 없는 소재는 모두 ETC 다"},
                    "quantityGram": {"type": "integer", "description": "가공할 양(gram)"},
                    "processTypes": {"type": "array", "minItems": 1, "maxItems": 8,
                      "items": {"type": "string", "enum": [%s]}},
                    "page": {"type": "integer", "minimum": 0, "default": 0}
                  },
                  "required": ["materialType", "quantityGram", "processTypes"],
                  "additionalProperties": false
                }"""
                .formatted(enumValues(MaterialType.class), enumValues(ProcessType.class));
    }

    @Override
    public boolean isWrite() {
        return false;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        String materialType = requiredText(arguments, "materialType");
        long quantityGram = requiredPositiveLong(arguments, "quantityGram");

        List<String> processTypes = new ArrayList<>();
        for (JsonNode node : arguments.path("processTypes")) {
            processTypes.add(node.asText());
        }
        if (processTypes.isEmpty()) {
            throw new AgentToolArgumentException("processTypes 는 하나 이상이어야 합니다.");
        }

        Long page = optionalLong(arguments, "page");

        // brandId 는 인증된 호출자다. 모델이 인자로 넣게 두면 남의 id 를 지어낼 수 있다.
        ProcessorSearchResponse response = processorSearch.search(
                context.userId(),
                materialType,
                String.valueOf(quantityGram),
                processTypes,
                String.valueOf(page == null ? 0 : page),
                String.valueOf(DEFAULT_SIZE));

        if (response.items().isEmpty()) {
            // 0건은 장애가 아니다. 모델이 조건을 어떻게 풀지 제안할 수 있게 사실을 돌려준다.
            return AgentToolResult.of(json(new EmptyResult(
                    0,
                    "조건에 맞는 가공업체가 없습니다. 수량을 조정하거나 공정을 나눠서 다시 찾아보세요.")));
        }
        Map<String, Object> payload = fields(response);
        Map<Long, String> names = companyNames.of(
                response.items().stream().map(ProcessorSearchResponse.Item::processorId).toList());
        payload.put("items", response.items().stream().map(item -> withText(item, names)).toList());
        return AgentToolResult.of(json(payload));
    }

    /**
     * 처리량과 금액을 문장으로 붙인다. 처리량은 gram/일이고, 모델이 그것을 kg 으로 옮기다
     * 틀리면 담당자는 업체를 잘못 고른다.
     */
    private Map<String, Object> withText(ProcessorSearchResponse.Item item, Map<Long, String> names) {
        Map<String, Object> row = fields(item);
        row.put("name", CompanyNames.pick(names, item.processorId(), item.name()));
        row.put("dailyCapacityText", Quantities.text(item.dailyCapacity()) + "/일");
        row.put("pricePerKgText", "kg당 %,d원".formatted(item.pricePerKg()));
        if (item.estimatedPrice() != null) {
            row.put("estimatedPriceText", "%,d원".formatted(item.estimatedPrice()));
        }
        return row;
    }

    private record EmptyResult(long total, String hint) {}
}
