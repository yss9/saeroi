package com.ssafy.upcycle.agent.tool.processing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.entity.JourneyPhase;
import com.ssafy.upcycle.agent.tool.AbstractAgentTool;
import com.ssafy.upcycle.agent.tool.AgentToolContext;
import com.ssafy.upcycle.agent.tool.AgentToolResult;
import com.ssafy.upcycle.agent.tool.PendingActionKind;
import com.ssafy.upcycle.processing.dto.ProcessingJobResponse;
import com.ssafy.upcycle.processing.dto.ProcessingPaymentReadyResponse;
import com.ssafy.upcycle.processing.dto.SelectProcessingQuoteRequest;
import com.ssafy.upcycle.processing.service.ProcessingJobService;
import com.ssafy.upcycle.processing.service.ProcessingPaymentService;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 견적을 고르고 가공비를 결제한다.
 *
 * <p>두 가지가 한 번에 일어난다 — 견적을 확정해 가공 작업을 만들고, 그 작업의 결제를
 * 준비한다. 소재 구매({@code purchase_material})와 같은 모양이다.
 *
 * <p><b>금액을 인자로 받지 않는다.</b> 가공비는 업체가 낸 견적에 적힌 값이고 서버가 그것을
 * 그대로 쓴다. 모델이 금액을 정할 수 있으면 1원짜리 결제를 만들 수 있다.
 *
 * <p><b>복귀 주소가 다르다.</b> 대화에서 시작한 결제라 카카오페이가 끝나면 작업 상세가 아니라
 * 대화창으로 돌아와야 한다. 작업 상세로 보내면 담당자가 하던 이야기를 잃는다.
 *
 * <p>고르는 순간 <b>다른 업체의 견적은 떨어진다.</b> 되돌릴 수 없으므로 결제 카드를 띄우기
 * 전에 담당자가 가격과 납기를 보고 골랐는지 확인한다.
 */
@Component
public class SelectQuoteTool extends AbstractAgentTool {

    private final ProcessingJobService jobService;
    private final ProcessingPaymentService paymentService;

    public SelectQuoteTool(
            ObjectMapper objectMapper,
            ProcessingJobService jobService,
            ProcessingPaymentService paymentService) {
        super(objectMapper);
        this.jobService = jobService;
        this.paymentService = paymentService;
    }

    @Override
    public String name() {
        return "select_quote";
    }

    @Override
    public String description() {
        return "견적 하나를 골라 가공 작업을 만들고 담당자에게 가공비 결제 카드를 띄운다. "
                + "고르면 같은 요청의 다른 견적은 떨어지므로, 가격과 납기를 설명하고 "
                + "담당자가 고른 뒤에 부른다. 카드를 띄운 뒤에는 결과를 기다린다.";
    }

    @Override
    public String parametersJsonSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "quoteId": {"type": "integer", "description": "list_quotes 가 준 견적 id"},
                    "summary": {"type": "string", "description": "카드에 보일 한 줄 설명"}
                  },
                  "required": ["quoteId", "summary"],
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

    @Override
    public AgentToolResult execute(AgentToolContext context, JsonNode arguments) {
        long quoteId = requiredPositiveLong(arguments, "quoteId");

        ProcessingJobResponse job = jobService.select(
                context.userId(), quoteId, new SelectProcessingQuoteRequest(PaymentMethod.KAKAO_PAY));

        // 대화에서 시작한 결제라 복귀 주소가 다르다.
        ProcessingPaymentReadyResponse ready =
                paymentService.ready(context.userId(), job.jobId(), true);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jobId", job.jobId());
        payload.put("fee", ready.amount());
        payload.put("paymentExpiresAt", ready.paymentExpiresAt());
        // 화면이 이 주소로 카카오페이를 띄운다.
        payload.put("redirectUrl", ready.redirectUrl());
        payload.put("live", ready.live());

        String message = "가공비 결제를 준비했습니다. 결제가 끝나면 소재를 업체로 보내는 단계로 넘어갑니다.";
        payload.put("message", message);
        return AgentToolResult.of(json(payload), message);
    }
}
