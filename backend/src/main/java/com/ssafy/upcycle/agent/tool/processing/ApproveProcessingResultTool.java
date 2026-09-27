package com.ssafy.upcycle.agent.tool.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.PendingActionKind;
import com.ssafy.upcycle.common.unit.Quantities;
import com.ssafy.upcycle.material.dto.MyMaterialResponse;
import com.ssafy.upcycle.material.service.MaterialQueryService;
import com.ssafy.upcycle.processing.dto.ProcessingJobResponse;
import com.ssafy.upcycle.processing.dto.ProcessingOutputResponse;
import com.ssafy.upcycle.processing.entity.ProcessType;
import com.ssafy.upcycle.processing.entity.ProcessingJobStatus;
import com.ssafy.upcycle.processing.entity.RequiredProcessing;
import com.ssafy.upcycle.processing.service.ProcessingJobService;
import com.ssafy.upcycle.processing.service.ProcessingJobWorkflowService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Optional;
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
    private final ProcessingJobWorkflowService jobWorkflow;

    public ApproveProcessingResultTool(
            ObjectMapper objectMapper,
            ProcessingJobService jobService,
            MaterialQueryService materialQuery,
            ProcessingJobWorkflowService jobWorkflow) {
        super(objectMapper);
        this.jobService = jobService;
        this.materialQuery = materialQuery;
        this.jobWorkflow = jobWorkflow;
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
    /**
     * <b>업체가 결과물을 돌려보낸 뒤에만</b> 카드를 띄운다.
     *
     * <p>이 검사가 없던 동안, 업체가 결과를 <b>올리기만 한</b> 시점({@code RESULT_SUBMITTED})
     * 에 승인 서명 카드가 자동으로 떴다. 남은 일 판정이 그 상태를 "승인할 차례" 로 봤기
     * 때문이다. 그런데 {@code receiveReturn} 은 {@code RETURN_SHIPPED} 에서만 받으므로 그
     * 카드는 <b>절대 성공할 수 없었다</b> — 담당자가 지갑을 열어 서명한 뒤에야 실패를 봤다.
     *
     * <p>결과를 올린 단계에서 담당자가 할 일은 결과를 확인하는 것이고, 반려할 게 있으면 가공
     * 화면에서 한다. 승인 서명은 물건이 돌아오는 중일 때다.
     */
    @Override
    public Optional<AgentToolResult> refuseBeforeProposal(AgentToolContext context, JsonNode arguments) {
        ProcessingJobResponse job = findJob(context, arguments);
        if (job == null
                || job.status() == ProcessingJobStatus.RETURN_SHIPPED
                || job.status() == ProcessingJobStatus.CONFIRMED) {
            return Optional.empty();
        }
        String hint = job.status() == ProcessingJobStatus.RESULT_SUBMITTED
                ? "업체가 결과를 올렸지만 아직 결과물을 돌려보내지 않았습니다. 지금은 결과를 담당자에게 "
                        + "보여 주고, 고칠 게 있으면 가공 화면에서 반려할 수 있다고 안내하세요. 승인 서명은 "
                        + "업체가 반환 발송한 뒤에 합니다."
                : "이 작업은 아직 승인할 단계가 아닙니다(%s). 업체가 결과물을 돌려보낸 뒤에 승인합니다."
                        .formatted(job.status());
        return Optional.of(AgentToolResult.of(json(Map.of(
                "error", "NOT_READY_FOR_APPROVAL",
                "jobId", job.jobId(),
                "status", job.status(),
                "hint", hint))));
    }

    /** 이미 승인된 작업이면 카드를 만들지도, 실행하지도 않는다. */
    @Override
    public Optional<String> stalenessReason(AgentToolContext context, JsonNode arguments) {
        if (carriesUserResult(arguments)) {
            return Optional.empty();
        }
        ProcessingJobResponse job = findJob(context, arguments);
        if (job != null && job.status() == ProcessingJobStatus.CONFIRMED) {
            return Optional.of("이 가공 작업은 이미 승인됐습니다. 결과물은 보유 소재에 들어와 있습니다.");
        }
        return Optional.empty();
    }

    /** 방금 승인 서명을 들고 온 요청이면, 그 서명이 DB 를 앞서 있을 수 있다. 판정하지 않는다. */
    private boolean carriesUserResult(JsonNode arguments) {
        return arguments.path("_userResult").hasNonNull("txHash");
    }

    /**
     * 카드 제목을 서버가 쓴다 — <b>무엇이 얼마나 돌아왔고 얼마가 줄었는지.</b>
     *
     * <p>승인하면 투입 소재가 소비되고 결과물이 새 소재가 된다. 되돌릴 수 없다. 그런데 카드에는 "폐데님 원단 10kg
     * 가공 — 결과 승인" 만 떠서, 담당자는 10kg 을 보내 9.5kg 이 돌아왔다는 것(가공 중 손실 0.5kg)을 모르고 승인했다.
     * 모델도 그 뒤 "가공된 폐데님 10kg" 이라고 말했다. 수동 화면은 결과물 목록과 손실을 함께 보여 준다.
     */
    @Override
    public Optional<String> serverSummary(AgentToolContext context, JsonNode arguments) {
        ProcessingJobResponse job = findJob(context, arguments);
        if (job == null || job.outputs() == null || job.outputs().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("%s — 결과 승인 · %s".formatted(subjectOf(context, job), resultText(job)));
    }

    /** "투입 10kg → 결과물 폐데님 가공 소재 9.5kg(A등급) · 가공 중 손실 0.5kg" */
    static String resultText(ProcessingJobResponse job) {
        List<String> outputs = new ArrayList<>();
        for (ProcessingOutputResponse output : job.outputs()) {
            outputs.add("%s %s(%s등급)".formatted(output.name(), Quantities.text(output.quantity()), output.grade()));
        }
        String loss = job.lossQuantity() > 0
                ? "가공 중 손실 " + Quantities.text(job.lossQuantity())
                : "손실 없음";
        return "투입 %s → 결과물 %s · %s".formatted(
                Quantities.text(job.inputQuantity()), String.join(", ", outputs), loss);
    }

    /** "폐데님 원단 10kg 가공". 투입 소재를 찾지 못하면 작업 번호로. */
    private String subjectOf(AgentToolContext context, ProcessingJobResponse job) {
        return materialQuery.findMyMaterials(context.userId()).stream()
                .filter(material -> job.inputMaterialId() != null && Objects.equals(material.materialId(), job.inputMaterialId()))
                .map(MyMaterialResponse::name)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .map(name -> "%s %s 가공".formatted(name, Quantities.text(job.inputQuantity())))
                .orElse("가공 작업 %d".formatted(job.jobId()));
    }

    private ProcessingJobResponse findJob(AgentToolContext context, JsonNode arguments) {
        long jobId = requiredPositiveLong(arguments, "jobId");
        return jobWorkflow.list(context.userId()).stream()
                .filter(job -> job.jobId() == jobId)
                .findFirst()
                .orElse(null);
    }

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
        String result;
        try {
            ProcessingJobResponse job = jobService.detail(context.userId(), jobId);
            result = resultText(job);
            List<MyMaterialResponse> mine = materialQuery.findMyMaterials(context.userId());
            for (ProcessingOutputResponse output : job.outputs()) {
                outputMaterialIds.add(output.materialId());
                mine.stream()
                        // 둘 다 Long 이다. == 는 객체를 비교해 id 가 127 을 넘으면 결과물을 찾지 못한다.
                        .filter(candidate -> Objects.equals(candidate.materialId(), output.materialId()))
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

        // **무엇이 얼마나 돌아왔는지 먼저 적는다.** 가공 중 줄어든 양은 이 문장이 아니면 대화 어디에도 없다.
        payload.put("result", result);
        String base = "가공 결과를 승인했습니다. " + result + ". 결과물이 새 소재(" + outputMaterialIds
                + ")로 발행되어 보유 목록에 들어왔습니다 — 이제 쓸 수 있는 양은 결과물의 양입니다. "
                + "다음 작업에는 이 새 id 를 씁니다. ";
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
