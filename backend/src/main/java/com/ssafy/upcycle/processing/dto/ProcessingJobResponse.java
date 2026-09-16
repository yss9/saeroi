package com.ssafy.upcycle.processing.dto;
import com.ssafy.upcycle.processing.entity.*;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import java.time.LocalDateTime;
import java.util.List;
public record ProcessingJobResponse(Long jobId, String jobCode, Long requestId, Long quoteId,
        Long brandId, Long processorId, long fee, PaymentMethod paymentMethod, String paymentReference,
        long paymentAmount, ProcessingJobStatus status, LocalDateTime paymentExpiresAt, LocalDateTime createdAt,
        Long chainJobId,
        Long inputMaterialId, long inputQuantity, long lossQuantity, int resultRevision,
        String carrier, String trackingNumber, String returnCarrier, String returnTrackingNumber,
        String returnShippingEvidenceHash, String rejectionReason,
        LocalDateTime shippedAt, LocalDateTime receivedAt, LocalDateTime startedAt,
        LocalDateTime resultSubmittedAt, LocalDateTime rejectedAt, LocalDateTime returnShippedAt, LocalDateTime completedAt,
        LocalDateTime processorSubmittedAt, LocalDateTime brandReviewedAt,
        List<ProcessingOutputResponse> outputs) {
    public static ProcessingJobResponse of(ProcessingJob j) { return new ProcessingJobResponse(j.getId(), j.getJobCode(),
            j.getProcessingRequestId(), j.getProcessingQuoteId(), j.getBrandId(), j.getProcessorId(), j.getFee(),
            j.getPaymentMethod(), j.getPaymentReference(), j.getPaymentAmount(), j.getStatus(), j.getPaymentExpiresAt(), j.getCreatedAt(),
            j.getChainJobId(), null, 0, j.getLossQuantity(), j.getResultRevision(), j.getCarrier(), j.getTrackingNumber(),
            j.getReturnCarrier(), j.getReturnTrackingNumber(), j.getReturnShippingEvidenceHash(), j.getRejectionReason(),
            j.getShippedAt(), j.getReceivedAt(), j.getStartedAt(), j.getResultSubmittedAt(), j.getRejectedAt(),
            j.getReturnShippedAt(), j.getCompletedAt(),
            j.getProcessorSubmittedAt(), j.getBrandReviewedAt(), List.of()); }
    public static ProcessingJobResponse of(ProcessingJob j, ProcessingInput input, List<ProcessingOutput> outputs) {
        ProcessingJobResponse base = of(j);
        return new ProcessingJobResponse(base.jobId(), base.jobCode(), base.requestId(), base.quoteId(),
                base.brandId(), base.processorId(), base.fee(), base.paymentMethod(), base.paymentReference(),
                base.paymentAmount(), base.status(), base.paymentExpiresAt(), base.createdAt(), base.chainJobId(), input.getMaterialId(),
                input.getQuantity(), j.getLossQuantity(), j.getResultRevision(), j.getCarrier(), j.getTrackingNumber(),
                j.getReturnCarrier(), j.getReturnTrackingNumber(), j.getReturnShippingEvidenceHash(), j.getRejectionReason(), j.getShippedAt(), j.getReceivedAt(),
                j.getStartedAt(), j.getResultSubmittedAt(), j.getRejectedAt(), j.getReturnShippedAt(), j.getCompletedAt(),
                j.getProcessorSubmittedAt(), j.getBrandReviewedAt(),
                outputs.stream().map(ProcessingOutputResponse::of).toList());
    }
}
