package com.ssafy.upcycle.processing.dto;
import com.ssafy.upcycle.processing.entity.*;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import java.time.LocalDateTime;
public record ProcessingJobResponse(Long jobId, String jobCode, Long requestId, Long quoteId,
        Long brandId, Long processorId, long fee, PaymentMethod paymentMethod, String paymentReference,
        long paymentAmount, long lossQuantity, ProcessingJobStatus status,
        LocalDateTime paymentExpiresAt, LocalDateTime processorSubmittedAt,
        LocalDateTime brandReviewedAt, LocalDateTime createdAt, LocalDateTime shippedAt,
        LocalDateTime receivedAt, LocalDateTime completedAt) {
    public static ProcessingJobResponse of(ProcessingJob j) { return new ProcessingJobResponse(j.getId(), j.getJobCode(),
            j.getProcessingRequestId(), j.getProcessingQuoteId(), j.getBrandId(), j.getProcessorId(), j.getFee(),
            j.getPaymentMethod(), j.getPaymentReference(), j.getPaymentAmount(), j.getLossQuantity(), j.getStatus(),
            j.getPaymentExpiresAt(), j.getProcessorSubmittedAt(), j.getBrandReviewedAt(), j.getCreatedAt(),
            j.getShippedAt(), j.getReceivedAt(), j.getCompletedAt()); }
}
