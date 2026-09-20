package com.ssafy.upcycle.agent.tool.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.PendingActionKind;
import com.ssafy.upcycle.processing.dto.ProcessingPaymentReadyResponse;
import com.ssafy.upcycle.processing.entity.ProcessingJob;
import com.ssafy.upcycle.processing.entity.ProcessingJobStatus;
import com.ssafy.upcycle.processing.repository.ProcessingJobRepository;
import com.ssafy.upcycle.processing.service.ProcessingPaymentService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 가공비를 결제한다.
 *
 * <h2>고를 견적이 없다</h2>
 *
 * <p>가공 요청을 만들면 <b>그 자리에서 작업과 금액이 정해진다.</b> 금액은 업체가 등록한
 * kg당 단가로 서버가 계산한다({@code ProcessingEstimate}). 그래서 업체 비교는 요청
 * <b>전에</b> {@code search_processors} 에서 끝난다 — 거기서 업체별 예상 금액과 소요일이
 * 함께 온다.
 *
 * <p>담당자가 할 일은 결제뿐이고, 결제가 끝나면 가공사의 수락을 기다린다. 수락 기한이
 * 지나면 배치가 정리하고 환불한다({@code ProcessingAcceptanceExpiryWorker}).
 *
 * <p><b>금액을 인자로 받지 않는다.</b> 작업에 적힌 값을 서버가 그대로 쓴다. 모델이 금액을
 * 정할 수 있으면 1원짜리 결제를 만들 수 있다.
 *
 * <p><b>복귀 주소가 다르다.</b> 대화에서 시작한 결제라 카카오페이가 끝나면 작업 상세가 아니라
 * 대화창으로 돌아와야 한다. 작업 상세로 보내면 담당자가 하던 이야기를 잃는다.
 */
@Component
public class PayProcessingFeeTool extends AbstractAgentTool {

    private final ProcessingPaymentService paymentService;
    // 카드가 아직 유효한지 보려고 작업을 읽는다.
    private final ProcessingJobRepository jobs;

    public PayProcessingFeeTool(
            ObjectMapper objectMapper,
            ProcessingPaymentService paymentService,
            ProcessingJobRepository jobs) {
        super(objectMapper);
        this.paymentService = paymentService;
        this.jobs = jobs;
    }

    @Override
    public String name() {
        return "pay_processing_fee";
    }

    @Override
    public String description() {
        return "가공 요청으로 만들어진 작업의 가공비 결제 카드를 담당자에게 띄운다. "
                + "create_processing_request 응답의 jobId 와 fee 를 그대로 쓴다. "
                + "결제가 끝나면 가공사가 수락해야 작업이 시작된다. "
                + "카드를 띄운 뒤에는 결과를 기다린다 — 결제됐다고 가정하지 않는다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "jobId": {"type": "integer", "description": "create_processing_request 가 준 작업 id"},
                    "summary": {"type": "string", "description": "카드에 보일 한 줄 설명"}
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
        return PendingActionKind.PAYMENT;
    }

    /**
     * 이미 결제된 작업인가.
     *
     * <p>리포지토리로 직접 읽는다 — {@code @Transactional} 서비스를 부르면 그 안의 예외를
     * 잡아도 Spring 이 바깥 트랜잭션을 rollback-only 로 표시해 승인 전체가 커밋되지 않는다.
     */
    @Override
    public Optional<String> stalenessReason(AgentToolContext context, JsonNode arguments) {
        ProcessingJob job = jobs
                .findById(requiredPositiveLong(arguments, "jobId"))
                .filter(candidate -> candidate.getBrandId().equals(context.userId()))
                .orElse(null);
        if (job == null) {
            // 없거나 남의 작업이면 판정하지 않는다. 승인 시점에 도메인이 거절할 일이다.
            return Optional.empty();
        }
        if (job.getStatus() != ProcessingJobStatus.CREATED) {
            return Optional.of(
                    "이 작업은 이미 결제가 끝났거나 결제할 수 있는 상태가 아닙니다. "
                            + "get_my_materials 나 작업 현황으로 지금 상태를 확인하세요.");
        }
        return Optional.empty();
    }

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        long jobId = requiredPositiveLong(arguments, "jobId");

        // 대화에서 시작한 결제라 복귀 주소가 다르다.
        ProcessingPaymentReadyResponse ready = paymentService.ready(context.userId(), jobId, true);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jobId", jobId);
        payload.put("fee", ready.amount());
        payload.put("paymentExpiresAt", ready.paymentExpiresAt());
        // 화면이 이 주소로 카카오페이를 띄운다.
        payload.put("redirectUrl", ready.redirectUrl());
        payload.put("live", ready.live());

        String message = "가공비 결제를 준비했습니다. 결제가 끝나면 가공사가 수락해야 작업이 시작됩니다.";
        payload.put("message", message);
        return AgentToolResult.of(json(payload), message);
    }
}
