package com.ssafy.upcycle.agent.tool.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolArgumentException;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.PendingActionKind;
import com.ssafy.upcycle.material.dto.MyMaterialResponse;
import com.ssafy.upcycle.material.service.MaterialQueryService;
import com.ssafy.upcycle.processing.dto.CreateProcessingRequest;
import com.ssafy.upcycle.processing.dto.ProcessingRequestResponse;
import com.ssafy.upcycle.processing.entity.ProcessType;
import com.ssafy.upcycle.processing.entity.RequiredProcessing;
import com.ssafy.upcycle.processing.service.ProcessingRequestService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 가공업체에 작업을 요청한다. 요청하는 순간 업체의 등록 단가로 가공비가 계산되고 작업이
 * 함께 생긴다 — <b>견적을 기다리는 구간은 없다.</b> 진행 상태와 가공비는
 * {@code get_my_processing} 으로 본다.
 *
 * <p><b>필수 공정을 대신 채워 넣지 않는다.</b> 빠진 것이 있으면 요청을 만들지 않고 무엇이
 * 빠졌는지 돌려준다. 자동으로 넣어 주면 결과는 같아 보여도 담당자는 자기가 무엇에 돈을
 * 내는지 모른 채 진행하게 된다 — 가공비는 담당자 돈이고, 공정이 늘면 납기도 늘어난다.
 *
 * <p>대신 생산 단계까지 가서 막히는 것도 막는다. 필수 공정 검사는 생산 확정에서 일어나는데
 * ({@code ProductionConfirmService}), 거기서 막히면 이미 가공비를 내고 물건을 보내고 받은
 * 뒤다. 그래서 요청을 만드는 이 자리에서 한 번 더 본다.
 *
 * <p>업체 한 곳에 한 건이다. 여러 곳의 견적을 비교하려면 업체마다 요청을 낸다 —
 * {@code search_processors} 가 후보를 준다.
 */
@Component
public class CreateProcessingRequestTool extends AbstractAgentTool {

    private final ProcessingRequestService requestService;
    private final MaterialQueryService materialQuery;

    public CreateProcessingRequestTool(
            ObjectMapper objectMapper,
            ProcessingRequestService requestService,
            MaterialQueryService materialQuery) {
        super(objectMapper);
        this.requestService = requestService;
        this.materialQuery = materialQuery;
    }

    @Override
    public String name() {
        return "create_processing_request";
    }

    @Override
    public String description() {
        return "가공업체에 작업을 요청한다. 요청하면 그 자리에서 가공비가 정해지고 작업이 생긴다. "
                + "진행 상태와 가공비는 get_my_processing 으로 확인한다. "
                + "부르기 전에 check_processing_plan 으로 필수 공정을 확인하고 담당자에게 설명해야 한다. "
                + "필수 공정이 빠져 있으면 요청이 만들어지지 않는다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "materialId": {"type": "integer"},
                    "processorId": {"type": "integer", "description": "search_processors 가 준 업체 id"},
                    "quantityGram": {"type": "integer", "description": "맡길 양(gram)"},
                    "processTypes": {"type": "array", "minItems": 1, "maxItems": 8,
                      "items": {"type": "string", "enum": [%s]},
                      "description": "담당자가 동의한 공정 전체. 필수 공정도 여기 포함해야 한다"},
                    "summary": {"type": "string", "description": "카드에 보일 한 줄 설명"}
                  },
                  "required": ["materialId", "processorId", "quantityGram", "processTypes", "summary"],
                  "additionalProperties": false
                }"""
                .formatted(enumValues(ProcessType.class));
    }

    @Override
    public Set<JourneyPhase> phases() {
        return Set.of(JourneyPhase.PROCESSING);
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
     * 필수 공정이 빠졌으면 <b>카드를 띄우기 전에</b> 돌려세운다.
     *
     * <p>승인 뒤에 거절하면 담당자는 이미 누른 다음이고, 화면에는 오류만 뜬다. 여기서 막으면
     * 모델이 담당자에게 무엇이 왜 필요한지 설명하고 다시 부를 수 있다.
     *
     * <p>여기서 대신 채우지 않는 이유는 가공비가 담당자 돈이기 때문이다. 공정이 늘면 납기도
     * 늘어난다. 자기가 무엇에 동의했는지 알아야 한다.
     */
    @Override
    public java.util.Optional<AgentToolResult> refuseBeforeProposal(
            AgentToolContext context, JsonNode arguments) {
        long materialId = requiredPositiveLong(arguments, "materialId");
        List<ProcessType> stillMissing =
                missingRequired(context.userId(), materialId, processTypes(arguments));
        if (stillMissing.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(AgentToolResult.of(json(Map.of(
                "error", "REQUIRED_PROCESSING_MISSING",
                "missingRequiredProcessTypes", stillMissing,
                "hint", "이 공정들이 빠지면 나중에 생산 확정 단계에서 막혀 소재를 다시 가공 "
                        + "보내야 합니다. 왜 필요한지 담당자에게 먼저 설명하고 동의를 받은 뒤, "
                        + "빠진 공정을 포함해 다시 부르세요. 임의로 넣지 마세요."))));
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        long materialId = requiredPositiveLong(arguments, "materialId");
        long processorId = requiredPositiveLong(arguments, "processorId");
        long quantityGram = requiredPositiveLong(arguments, "quantityGram");
        List<ProcessType> processTypes = processTypes(arguments);

        ProcessingRequestResponse created = requestService.create(
                context.userId(),
                new CreateProcessingRequest(materialId, processorId, quantityGram, processTypes));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("request", created);
        String message = "가공 요청을 보냈습니다. 업체가 견적을 내면 알려드리겠습니다.";
        payload.put("message", message);
        return AgentToolResult.of(json(payload), message);
    }

    /** 담당자가 동의한 공정 목록에서 빠진 필수 공정. 이미 거친 것은 다시 요구하지 않는다. */
    private List<ProcessType> missingRequired(
            Long userId, long materialId, List<ProcessType> requested) {
        MyMaterialResponse material = materialQuery.findMyMaterials(userId).stream()
                .filter(candidate -> candidate.materialId() == materialId)
                .findFirst()
                .orElse(null);
        if (material == null) {
            // 소유 검사는 도메인 서비스가 한다. 여기서는 필수 공정을 판단할 근거가 없을 뿐이다.
            return List.of();
        }
        List<ProcessType> completed = material.completedProcessTypes() == null
                ? List.of()
                : material.completedProcessTypes();

        List<ProcessType> missing = new ArrayList<>();
        for (ProcessType required : RequiredProcessing.missing(material.rootOrigin(), completed)) {
            if (!requested.contains(required)) {
                missing.add(required);
            }
        }
        return missing;
    }

    private List<ProcessType> processTypes(JsonNode arguments) {
        JsonNode node = arguments.get("processTypes");
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw new AgentToolArgumentException("processTypes 가 필요합니다.");
        }
        List<ProcessType> types = new ArrayList<>();
        for (JsonNode element : node) {
            try {
                ProcessType type = ProcessType.valueOf(element.asText());
                if (!types.contains(type)) {
                    types.add(type);
                }
            } catch (IllegalArgumentException invalid) {
                throw new AgentToolArgumentException("알 수 없는 공정입니다: " + element.asText());
            }
        }
        return types;
    }
}
