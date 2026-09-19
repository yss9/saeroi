package com.ssafy.upcycle.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.material.dto.MyMaterialResponse;
import com.ssafy.upcycle.material.service.MaterialQueryService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 담당자가 이미 보유한 소재.
 *
 * <p>마켓플레이스보다 먼저 이걸 보게 하는 것이 중요하다. 창고에 있는 소재를 두고 같은 것을
 * 또 사는 일이 실제로 일어나고, 대화로 하면 더 쉽게 일어난다.
 */
@Component
public class MyMaterialsTool extends AbstractAgentTool {

    private final MaterialQueryService materialQuery;

    public MyMaterialsTool(ObjectMapper objectMapper, MaterialQueryService materialQuery) {
        super(objectMapper);
        this.materialQuery = materialQuery;
    }

    @Override
    public String name() {
        return "get_my_materials";
    }

    @Override
    public String description() {
        return "담당자가 지금 보유한 소재 목록. 소재를 새로 사기 전에 반드시 먼저 확인한다. "
                + "quantity 는 보유량, available 은 다른 거래에 묶이지 않은 가용량(gram)이다.";
    }

    @Override
    public String parametersJsonSchema() {
        return NO_ARGUMENTS_SCHEMA;
    }

    @Override
    public Set<JourneyPhase> phases() {
        // 소재 확보 단계뿐 아니라 가공·생산에서도 "얼마 남았나" 를 확인해야 한다.
        return Set.of(
                JourneyPhase.MATERIAL_SOURCING, JourneyPhase.PROCESSING, JourneyPhase.PRODUCTION);
    }

    @Override
    public boolean isWrite() {
        return false;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        List<MyMaterialResponse> materials = materialQuery.findMyMaterials(context.userId());

        if (materials.isEmpty()) {
            return AgentToolResult.of(json(Map.of(
                    "materials", List.of(),
                    "hint", "보유한 소재가 없습니다. 마켓플레이스에서 찾아야 합니다.")));
        }
        return AgentToolResult.of(json(Map.of("materials", materials)));
    }
}
