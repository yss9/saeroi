package com.ssafy.upcycle.agent.tool.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.service.CompanyNames;
import com.ssafy.upcycle.blockchain.config.BlockchainProperties;
import com.ssafy.upcycle.agent.project.AgentProject;
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
import com.ssafy.upcycle.processing.service.ProcessorSearchService;
import com.ssafy.upcycle.processing.dto.ProcessorSearchResponse;
import com.ssafy.upcycle.common.unit.Quantities;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final ProcessorSearchService processorSearch;
    /** 체인을 쓰는 환경인지만 본다 — 안 쓰면 토큰이 애초에 없다. */
    private final BlockchainProperties blockchain;
    /** 카드 제목의 업체는 대표자 이름이 아니라 업체명이다. */
    private final CompanyNames companyNames;

    public CreateProcessingRequestTool(
            ObjectMapper objectMapper,
            ProcessingRequestService requestService,
            MaterialQueryService materialQuery,
            ProcessorSearchService processorSearch,
            BlockchainProperties blockchain,
            CompanyNames companyNames) {
        super(objectMapper);
        this.requestService = requestService;
        this.materialQuery = materialQuery;
        this.processorSearch = processorSearch;
        this.blockchain = blockchain;
        this.companyNames = companyNames;
    }

    /**
     * 대화에서 맡긴 가공과 <b>그 가공에 넣은 소재</b>가 이 프로젝트의 것이 된다. 홈페이지에서
     * 사 둔 소재도 여기서 쓰는 순간 들어온다. 결과물은 작업에서 따라온다({@code AgentProjects}).
     */
    @Override
    public void claim(JsonNode arguments, JsonNode result, AgentProject.Builder project) {
        project.job(AgentProject.idAt(result, "request", "jobId"));
        project.material(AgentProject.idAt(result, "request", "materialId"));
        project.material(AgentProject.idAt(arguments, "materialId"));
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

        // **토큰이 없는 소재는 가공에 보낼 수 없다.** 체인에 작업을 올리려면 소재가 온체인
        // 토큰이어야 하고(ProcessingChainService.prepare), 그 검사는 소재를 인계하는
        // 순간에야 돈다 — 그때는 이미 가공비를 결제하고 업체가 수락한 뒤다.
        //
        // 실제로 그렇게 새어 나갔다. 생산 자투리 500g 으로 요청하고 2,500원을 결제하고 업체
        // 수락까지 받은 뒤, 인계 서명에서 "현재 상태에서는 가공 요청을 변경할 수 없습니다"
        // 로 막혔다. 원인을 말해 주지 않는 문구라 무엇이 잘못됐는지 알 수 없었고, 되돌릴
        // 방법도 없었다. 그래서 카드를 띄우기 전에 본다.
        java.util.Optional<AgentToolResult> notOnChain = refuseWhenNotOnChain(context, materialId);
        if (notOnChain.isPresent()) {
            return notOnChain;
        }

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

    /**
     * 소재가 <b>온체인 토큰으로 발행됐는가.</b>
     *
     * <p>체인을 쓰지 않는 환경은 보지 않는다 — 거기서는 토큰이 애초에 없고, 막으면 가공이
     * 통째로 불가능해진다. {@code ProcessingChainService} 도 같은 이유로 설정을 먼저 본다.
     */
    private java.util.Optional<AgentToolResult> refuseWhenNotOnChain(
            AgentToolContext context, long materialId) {
        if (!blockchain.isConfigured()) {
            return java.util.Optional.empty();
        }
        MyMaterialResponse material = materialQuery.findMyMaterials(context.userId()).stream()
                .filter(each -> each.materialId() == materialId)
                .findFirst()
                .orElse(null);
        if (material == null || material.tokenId() != null) {
            // 없는 소재는 여기서 판정하지 않는다 — 도메인이 거절할 일이다.
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(AgentToolResult.of(json(Map.of(
                "error", "MATERIAL_NOT_ON_CHAIN",
                "materialId", materialId,
                "materialName", material.name(),
                "hint", "이 소재는 아직 블록체인에 발행되지 않아 가공을 맡길 수 없습니다. "
                        + "가공은 체인에 작업을 올려야 진행되고, 그 단계에서 막히면 이미 "
                        + "가공비를 낸 뒤가 됩니다. 담당자에게 이 사실을 그대로 알리고, "
                        + "다른 소재를 쓸지 물어보세요. 요청을 다시 시도하지 마세요."))));
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

        // **견적을 기다린다고 말하지 않는다.** 그런 단계가 없다 — 요청하는 순간 업체의 등록
        // 단가로 가공비가 정해지고 작업이 함께 생긴다(ProcessingRequestService.create).
        //
        // 예전 문구가 "업체가 견적을 내면 알려드리겠습니다" 였고, 모델은 그것을 그대로
        // 담당자에게 옮겼다. 담당자는 기다리면 되는 줄 알고 결제를 하지 않았고, 작업은
        // CREATED 로 멈춘 채 결제 기한만 흘렀다. 모델이 지어낸 말이 아니라 우리가 준 문장이
        // 사실과 반대였다.
        String message = "가공 요청이 접수됐습니다. 작업 %d번, 가공비 %,d원입니다. 이어서 결제가 필요합니다."
                .formatted(created.jobId(), created.fee());
        payload.put("message", message);
        payload.put(
                "nextStep",
                "pay_processing_fee 에 jobId %d 와 fee %d 를 그대로 넘겨 결제 카드를 띄운다. "
                        .formatted(created.jobId(), created.fee())
                        + "견적을 기다리는 단계는 없다 — 금액은 이미 정해졌다.");
        return AgentToolResult.of(json(payload), message);
    }

    /**
     * 카드 제목을 서버가 쓴다 — <b>무엇을 어느 업체에 얼마에 맡기는지.</b>
     *
     * <p>모델이 쓴 요약은 "울 원단 5kg 세척·분해·선별 가공" 뿐이라, 담당자는 업체와 가공비를 모른 채 요청을
     * 승인했다. 수동 화면은 업체를 고를 때 예상 금액과 소요일을 나란히 보여 준다. 여기서도 같은 검색으로 그
     * 업체의 예상 금액과 소요일을 붙인다. 찾지 못하면 금액 없이 적는다 — 금액을 짐작하지 않는다.
     */
    @Override
    public Optional<String> serverSummary(AgentToolContext context, JsonNode arguments) {
        long materialId = arguments.path("materialId").asLong();
        long quantityGram = arguments.path("quantityGram").asLong();
        List<ProcessType> types = validProcessTypes(arguments);
        if (materialId <= 0 || quantityGram <= 0 || types.isEmpty()) {
            return Optional.empty();
        }
        MyMaterialResponse material = materialQuery.findMyMaterials(context.userId()).stream()
                .filter(candidate -> candidate.materialId() == materialId)
                .findFirst()
                .orElse(null);
        if (material == null || material.name() == null) {
            return Optional.empty();
        }
        String head = "%s %s 가공 — %s".formatted(
                material.name(), Quantities.text(quantityGram), ProcessLabels.joined(types));
        return Optional.of(estimate(context, material, quantityGram, types, arguments.path("processorId").asLong())
                .map(item -> head + " · %s · 예상 %,d원 · %d일".formatted(
                        companyNames.of(item.processorId(), item.name()),
                        item.estimatedPrice(), item.estimatedDays()))
                .orElse(head));
    }

    /**
     * 그 업체의 예상 금액과 소요일. 업체 검색과 같은 계산이다.
     *
     * <p><b>검색이 거절할 인자는 부르기 전에 걸러 낸다.</b> 이 요약은 카드를 띄우는 트랜잭션 안에서 만들어지는데,
     * 거기서 검색이 예외를 던지면 트랜잭션 전체가 롤백 대상이 되어 턴이 통째로 실패한다.
     */
    private Optional<ProcessorSearchResponse.Item> estimate(
            AgentToolContext context, MyMaterialResponse material, long quantityGram, List<ProcessType> types,
            long processorId) {
        if (processorId <= 0 || material.materialType() == null || types.size() > 8) {
            return Optional.empty();
        }
        ProcessorSearchResponse found = processorSearch.search(
                context.userId(),
                material.materialType().name(),
                String.valueOf(quantityGram),
                types.stream().map(ProcessType::name).toList(),
                "0",
                "50");
        return found.items().stream()
                .filter(item -> item.processorId() != null && item.processorId() == processorId)
                .filter(item -> item.estimatedPrice() != null && item.estimatedDays() != null)
                .findFirst();
    }

    /** 인자의 공정 목록. 읽을 수 없는 값이 있으면 비운다 — 요약은 예외를 올리지 않는다. */
    private static List<ProcessType> validProcessTypes(JsonNode arguments) {
        JsonNode node = arguments.get("processTypes");
        if (node == null || !node.isArray() || node.isEmpty()) {
            return List.of();
        }
        List<ProcessType> types = new ArrayList<>();
        for (JsonNode element : node) {
            try {
                ProcessType type = ProcessType.valueOf(element.asText());
                if (!types.contains(type)) {
                    types.add(type);
                }
            } catch (IllegalArgumentException invalid) {
                return List.of();
            }
        }
        return types;
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
