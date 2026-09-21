package com.ssafy.upcycle.processing.dto;

import com.ssafy.upcycle.processing.entity.*;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 가공 작업 한 건.
 *
 * <p><b>{@code quoteId} 가 사라지고 수락 관련 값이 들어왔다.</b> 견적이 없어졌으므로 가리킬
 * 견적도 없고, 대신 가공사가 답해야 하는 기한({@code acceptExpiresAt})과 답한 결과
 * ({@code acceptedAt}·{@code declinedAt}·{@code declineReason})가 화면에 필요하다.
 *
 * <p>{@code declineReason} 과 {@code rejectionReason} 은 <b>다른 값</b>이다. 앞은 가공사가
 * 일을 안 받은 이유이고, 뒤는 브랜드가 나온 결과를 반려한 이유다. 한 칸으로 합치면
 * 화면이 "거절 사유" 를 어느 쪽 문맥에서 보여줘야 할지 알 수 없다.
 *
 * <p>{@code refundedAt} 이 채워져 있으면 가공비가 돌아갔다는 뜻이다 — 거절·만료된 작업에서만
 * 값이 있다.
 */
public record ProcessingJobResponse(Long jobId, String jobCode, Long requestId,
        Long brandId, Long processorId, long fee, PaymentMethod paymentMethod, String paymentReference,
        long paymentAmount, ProcessingJobStatus status, LocalDateTime paymentExpiresAt,
        LocalDateTime acceptExpiresAt, LocalDateTime acceptedAt, LocalDateTime declinedAt,
        String declineReason, LocalDateTime refundedAt, LocalDateTime createdAt,
        Long chainJobId,
        Long inputMaterialId, long inputQuantity, long lossQuantity, int resultRevision,
        String carrier, String trackingNumber, String returnCarrier, String returnTrackingNumber,
        String returnShippingEvidenceHash, String rejectionReason,
        LocalDateTime shippedAt, LocalDateTime receivedAt, LocalDateTime startedAt,
        LocalDateTime resultSubmittedAt, LocalDateTime rejectedAt, LocalDateTime returnShippedAt,
        LocalDateTime completedAt,
        LocalDateTime processorSubmittedAt, LocalDateTime brandReviewedAt,
        List<ProcessingOutputResponse> outputs,
        /**
         * 온체인 잠금이 남아 있어 브랜드가 풀어 줘야 하는가.
         *
         * <p>거절·만료는 서버가 DB 와 환불만 정리한다. 체인의 Lock 은 브랜드 지갑이
         * {@code cancelJob} 을 서명해야 풀린다 — 서버에 개인키를 두지 않기 때문이다.
         * 이 값이 참인 동안 그 수량은 온체인 가용 잔량에서 빠져 있다.
         */
        boolean chainReleaseRequired) {

    public static ProcessingJobResponse of(ProcessingJob j) {
        return new ProcessingJobResponse(j.getId(), j.getJobCode(), j.getProcessingRequestId(),
                j.getBrandId(), j.getProcessorId(), j.getFee(), j.getPaymentMethod(),
                j.getPaymentReference(), j.getPaymentAmount(), j.getStatus(), j.getPaymentExpiresAt(),
                j.getAcceptExpiresAt(), j.getAcceptedAt(), j.getDeclinedAt(), j.getDeclineReason(),
                j.getRefundedAt(), j.getCreatedAt(), j.getChainJobId(), null, 0, j.getLossQuantity(),
                j.getResultRevision(), j.getCarrier(), j.getTrackingNumber(), j.getReturnCarrier(),
                j.getReturnTrackingNumber(), j.getReturnShippingEvidenceHash(), j.getRejectionReason(),
                j.getShippedAt(), j.getReceivedAt(), j.getStartedAt(), j.getResultSubmittedAt(),
                j.getRejectedAt(), j.getReturnShippedAt(), j.getCompletedAt(),
                j.getProcessorSubmittedAt(), j.getBrandReviewedAt(), List.of(), chainReleaseRequired(j));
    }

    public static ProcessingJobResponse of(
            ProcessingJob j, ProcessingInput input, List<ProcessingOutput> outputs) {
        ProcessingJobResponse base = of(j);
        return new ProcessingJobResponse(base.jobId(), base.jobCode(), base.requestId(),
                base.brandId(), base.processorId(), base.fee(), base.paymentMethod(),
                base.paymentReference(), base.paymentAmount(), base.status(), base.paymentExpiresAt(),
                base.acceptExpiresAt(), base.acceptedAt(), base.declinedAt(), base.declineReason(),
                base.refundedAt(), base.createdAt(), base.chainJobId(),
                input.getMaterialId(), input.getQuantity(), j.getLossQuantity(), j.getResultRevision(),
                j.getCarrier(), j.getTrackingNumber(), j.getReturnCarrier(), j.getReturnTrackingNumber(),
                j.getReturnShippingEvidenceHash(), j.getRejectionReason(), j.getShippedAt(),
                j.getReceivedAt(), j.getStartedAt(), j.getResultSubmittedAt(), j.getRejectedAt(),
                j.getReturnShippedAt(), j.getCompletedAt(),
                j.getProcessorSubmittedAt(), j.getBrandReviewedAt(),
                outputs.stream().map(ProcessingOutputResponse::of).toList(), base.chainReleaseRequired());
    }

    private static boolean chainReleaseRequired(ProcessingJob j) {
        if (j.getChainJobId() == null) return false;
        return j.getStatus() == ProcessingJobStatus.DECLINED
                || j.getStatus() == ProcessingJobStatus.EXPIRED
                || j.getStatus() == ProcessingJobStatus.CANCELED;
    }
}
