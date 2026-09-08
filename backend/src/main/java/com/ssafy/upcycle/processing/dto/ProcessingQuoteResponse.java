package com.ssafy.upcycle.processing.dto;
import com.ssafy.upcycle.processing.entity.*;
import java.time.LocalDateTime;
public record ProcessingQuoteResponse(Long quoteId, Long requestId, Long processorId, String processorName,
        long price, int leadDays, String memo, ProcessingQuoteStatus status, LocalDateTime createdAt) {
    public static ProcessingQuoteResponse of(ProcessingQuote q, String name) {
        return new ProcessingQuoteResponse(q.getId(), q.getProcessingRequestId(), q.getProcessorId(), name,
                q.getPrice(), q.getLeadDays(), q.getMemo(), q.getStatus(), q.getCreatedAt());
    }
}
