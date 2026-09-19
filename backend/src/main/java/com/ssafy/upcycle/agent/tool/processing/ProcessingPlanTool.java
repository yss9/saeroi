package com.ssafy.upcycle.agent.tool.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.material.dto.MyMaterialResponse;
import com.ssafy.upcycle.material.service.MaterialQueryService;
import com.ssafy.upcycle.processing.entity.ProcessType;
import com.ssafy.upcycle.processing.entity.RequiredProcessing;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 이 소재를 제품으로 만들려면 어떤 공정이 남았는지 알려준다.
 *
 * <p><b>가공을 요청하기 전에 반드시 먼저 부른다.</b> 폐소재는 세척·해체·선별을 모두 거쳐야
 * 제품이 될 수 있는데({@link RequiredProcessing}), 그 검사는 한참 뒤인 생산 확정에서야
 * 일어난다. 지금 알려주지 않으면 담당자는 빠뜨린 공정을 모른 채 가공비를 내고, 물건을
 * 보내고, 결과를 받은 뒤에야 막힌다 — 그때는 소재를 다시 가공에 보내야 한다.
 *
 * <p><b>그렇다고 몰래 채워 넣지도 않는다.</b> 필수 공정을 tool 이 조용히 추가하면 담당자는
 * 자기가 무엇에 돈을 냈는지 모른다. 여기서 "무엇이 왜 필요한지" 를 내주고, 설명하고 동의를
 * 받는 것은 대화가 한다.
 *
 * <p>추천 공정은 여기서 정하지 않는다. 만들려는 제품과 원단 특성에 달린 판단이라 규칙으로
 * 굳힐 수 없고, 모델이 이유와 함께 제안한 뒤 담당자가 고르는 편이 낫다.
 */
@Component
public class ProcessingPlanTool extends AbstractAgentTool {

    private final MaterialQueryService materialQuery;

    public ProcessingPlanTool(ObjectMapper objectMapper, MaterialQueryService materialQuery) {
        super(objectMapper);
        this.materialQuery = materialQuery;
    }

    @Override
    public String name() {
        return "check_processing_plan";
    }

    @Override
    public String description() {
        return "이 소재가 제품이 되기까지 아직 거쳐야 하는 필수 공정을 확인한다. "
                + "가공을 요청하기 전에 반드시 먼저 부르고, 결과를 담당자에게 설명한다. "
                + "폐소재는 세척·해체·선별을 모두 거쳐야 제품이 될 수 있다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "materialId": {"type": "integer"}
                  },
                  "required": ["materialId"],
                  "additionalProperties": false
                }""";
    }

    @Override
    public Set<JourneyPhase> phases() {
        // 생산 단계에서도 "왜 이 소재를 못 쓰는가" 를 설명해야 한다.
        return Set.of(JourneyPhase.PROCESSING, JourneyPhase.PRODUCTION);
    }

    @Override
    public boolean isWrite() {
        return false;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        long materialId = requiredPositiveLong(arguments, "materialId");

        // 보유 목록에서 찾는다 — 소유 검사를 여기서 따로 하지 않기 위해서다.
        MyMaterialResponse material = materialQuery.findMyMaterials(context.userId()).stream()
                .filter(candidate -> candidate.materialId() == materialId)
                .findFirst()
                .orElse(null);
        if (material == null) {
            return AgentToolResult.of(json(Map.of(
                    "error", "MATERIAL_NOT_OWNED",
                    "hint", "보유하지 않은 소재입니다. get_my_materials 로 먼저 확인하세요.")));
        }

        List<ProcessType> completed =
                material.completedProcessTypes() == null ? List.of() : material.completedProcessTypes();
        List<ProcessType> missing = RequiredProcessing.missing(material.rootOrigin(), completed);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("materialId", materialId);
        payload.put("materialName", material.name());
        payload.put("rootOrigin", material.rootOrigin());
        payload.put("completedProcessTypes", completed);
        payload.put("requiredProcessTypes", RequiredProcessing.all());
        payload.put("missingRequiredProcessTypes", missing);
        payload.put("readyForProduction", missing.isEmpty());
        payload.put("hint", missing.isEmpty()
                ? "필수 공정을 모두 거쳤습니다. 추가 공정은 제품 특성에 따라 담당자와 상의해 정하세요."
                : "가공을 요청하기 전에 이 공정들이 왜 필요한지 담당자에게 먼저 설명하고 동의를 받으세요. "
                        + "이것을 빠뜨리면 생산 확정 단계에서 막혀 소재를 다시 가공 보내야 합니다.");
        return AgentToolResult.of(json(payload));
    }
}
