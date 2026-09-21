package com.ssafy.upcycle.agent.tool.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.PendingActionKind;
import com.ssafy.upcycle.material.dto.MyMaterialResponse;
import com.ssafy.upcycle.material.service.MaterialQueryService;
import com.ssafy.upcycle.processing.dto.ProcessingJobResponse;
import com.ssafy.upcycle.processing.dto.ProcessingOutputResponse;
import com.ssafy.upcycle.processing.entity.ProcessType;
import com.ssafy.upcycle.processing.entity.RequiredProcessing;
import com.ssafy.upcycle.processing.service.ProcessingJobService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 업체가 돌려보낸 가공 결과를 받아들인다. <b>여기서 새 소재가 생긴다.</b>
 *
 * <p>승인하면 업체가 쥐고 있던 투입 소재가 소비되고, 결과물이 새 소재로 발행되어 담당자의
 * 보유 목록에 들어온다. 부모-자식이 {@code material_lineage} 로 이어져 제품 패스포트의
 * 이력이 된다. 되돌릴 수 없다 — 그래서 서명을 받는다.
 *
 * <h2>서버가 체인에 쓰지 않는다</h2>
 *
 * <p>{@code request_chain_order} 와 같다. 프론트가 온체인 승인({@code approveProcessingResult})
 * 을 서명하고, 그 영수증을 기존 API({@code POST /processing-jobs/{id}/return/receive})로
 * 붙인다 — <b>화면에서 승인하는 것과 같은 경로</b>다. 서버는 영수증을 RPC 로 확인한 뒤에야
 * 소재를 발행한다({@code ProcessingChainService#receiveReturn}).
 *
 * <p>도메인에 서명 없이 승인하는 메서드({@code ProcessingJobWorkflowService#approve})가
 * 따로 있지만 쓰지 않는다. 그쪽은 어떤 화면도 부르지 않는 경로이고, 체인 영수증을 확인하지
 * 않으며 소비하는 잠금도 다르다. 대화가 그 길로 가면 화면으로 한 승인과 대화로 한 승인이
 * 서로 다른 결과를 남긴다.
 *
 * <h2>업체가 결과를 돌려보낸 뒤에만 가능하다</h2>
 *
 * <p>{@code receiveReturn} 은 작업이 {@code RETURN_SHIPPED} 일 때만 받는다. 결과만 올라오고
 * 아직 발송 전이면 승인할 수 없다 — 물건이 오는 중인데 소유를 옮길 수는 없다.
 */
@Component
public class ApproveProcessingResultTool extends AbstractAgentTool {

    private final ProcessingJobService jobService;
    private final MaterialQueryService materialQuery;

    public ApproveProcessingResultTool(
            ObjectMapper objectMapper,
            ProcessingJobService jobService,
            MaterialQueryService materialQuery) {
        super(objectMapper);
        this.jobService = jobService;
        this.materialQuery = materialQuery;
    }

    @Override
    public String name() {
        return "approve_processing_result";
    }

    @Override
    public String description() {
        return "업체가 돌려보낸 가공 결과를 승인하도록 담당자에게 서명 카드를 띄운다. "
                + "승인하면 투입 소재가 소비되고 결과물이 새 소재로 발행되어 보유 목록에 들어온다. "
                + "되돌릴 수 없으므로 담당자가 결과와 수량을 확인했는지 먼저 묻는다. "
                + "결과가 잘못됐다면 승인하지 말고 가공 작업 화면에서 거절해야 한다고 안내한다. "
                + "업체가 결과를 발송한 뒤에만 쓸 수 있다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "jobId": {"type": "integer"},
                    "summary": {"type": "string",
                      "description": "담당자가 무엇에 서명하는지 한 줄로. 카드에 그대로 보인다"}
                  },
                  "required": ["jobId", "summary"],
                  "additionalProperties": false
                }""";
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
        return PendingActionKind.WALLET_SIGN;
    }

    /**
     * 담당자가 서명을 끝낸 뒤에만 불린다.
     *
     * <p>{@code txHash} 를 검증하지 않는다. {@code receiveReturn} 이 영수증을 RPC 로 확인하며
     * 이미 하고 있고, 여기서 또 하면 같은 규칙이 두 곳에 생긴다.
     */
    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        long jobId = requiredPositiveLong(arguments, "jobId");
        JsonNode userResult = arguments.path("_userResult");
        String txHash = userResult.path("txHash").asText(null);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jobId", jobId);
        payload.put("signed", txHash != null);
        payload.put("txHash", txHash);

        if (txHash == null) {
            String failed = "서명이 확인되지 않았습니다. 다시 시도해 주세요.";
            payload.put("message", failed);
            return AgentToolResult.of(json(payload), failed);
        }

        // 결과물의 id 와 남은 필수 공정을 함께 돌려준다. 자세한 이유는 outcomeOf 에 적었다.
        String message = outcomeOf(context, jobId, payload);
        payload.put("message", message);
        return AgentToolResult.of(json(payload), message);
    }

    /**
     * 승인 뒤에 무엇이 남았는지.
     *
     * <h2>새 소재의 id 를 반드시 알려 준다</h2>
     *
     * <p>승인하면 투입 소재는 소비되고 <b>다른 id 의 소재</b>가 생긴다. 모델이 앞선 턴에서
     * 기억한 id 로 다음 가공을 요청하면, 이미 사라진 소재를 가리키게 된다. 그래서 결과물의
     * id 를 결과에 실어 보낸다.
     *
     * <h2>"이제 생산으로" 라고 단정하지 않는다</h2>
     *
     * <p>필수 공정은 세척·해체·선별 셋이고, 업체 한 곳이 셋을 다 하지 않는 경우가 흔하다.
     * 그러면 가공은 <b>두 번, 세 번</b> 돌아간다 — 그때마다 소재가 새로 생기고 이력이
     * 혈통을 따라 쌓인다. 예전에는 몇 번째 승인이든 "이제 생산 단계로 넘어갈 수 있습니다"
     * 라고 말했는데, 공정이 남아 있으면 그건 거짓말이고 담당자는 생산 확정에서야 막힌다.
     * 거기까지 가면 이미 가공비를 내고 물건을 보내고 받은 뒤다.
     */
    private String outcomeOf(AgentToolContext context, long jobId, Map<String, Object> payload) {
        List<Long> outputMaterialIds = new ArrayList<>();
        List<ProcessType> stillMissing = new ArrayList<>();
        try {
            ProcessingJobResponse job = jobService.detail(context.userId(), jobId);
            List<MyMaterialResponse> mine = materialQuery.findMyMaterials(context.userId());
            for (ProcessingOutputResponse output : job.outputs()) {
                outputMaterialIds.add(output.materialId());
                mine.stream()
                        .filter(candidate -> candidate.materialId() == output.materialId())
                        .findFirst()
                        .ifPresent(material -> {
                            for (ProcessType missing : RequiredProcessing.missing(
                                    material.rootOrigin(), completedOf(material))) {
                                if (!stillMissing.contains(missing)) {
                                    stillMissing.add(missing);
                                }
                            }
                        });
            }
        } catch (RuntimeException failure) {
            // 조회가 흔들려도 승인 자체는 끝났다. 그 사실을 알리는 쪽이 먼저다.
            return "가공 결과를 승인했습니다. 결과물이 새 소재로 발행되어 보유 목록에 들어왔습니다. "
                    + "get_my_materials 로 새 소재를 확인하세요.";
        }

        payload.put("outputMaterialIds", outputMaterialIds);
        payload.put("missingRequiredProcessTypes", stillMissing);

        String base = "가공 결과를 승인했습니다. 결과물이 새 소재(" + outputMaterialIds
                + ")로 발행되어 보유 목록에 들어왔습니다. 다음 작업에는 이 새 id 를 씁니다. ";
        if (stillMissing.isEmpty()) {
            return base + "필수 공정이 모두 끝나 생산 단계로 넘어갈 수 있습니다.";
        }
        return base + "아직 " + stillMissing + " 이(가) 남아 생산 확정에서 막힙니다. "
                + "담당자에게 남은 공정을 알리고 이어서 맡길지 물어보세요.";
    }

    private List<ProcessType> completedOf(MyMaterialResponse material) {
        return material.completedProcessTypes() == null
                ? List.of()
                : material.completedProcessTypes();
    }
}
