package com.ssafy.upcycle.processing.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "processing_quote", uniqueConstraints = @UniqueConstraint(
        name = "uk_processing_quote_request_processor",
        columnNames = {"processing_request_id", "processor_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessingQuote {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "processing_request_id", nullable = false) private Long processingRequestId;
    @Column(name = "processor_id", nullable = false) private Long processorId;
    @Column(nullable = false) private long price;
    @Column(name = "lead_days", nullable = false) private int leadDays;
    @Column(columnDefinition = "TEXT") private String memo;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ProcessingQuoteStatus status;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    public static ProcessingQuote submit(Long requestId, Long processorId, long price, int leadDays,
            String memo, LocalDateTime now) {
        if (requestId == null || processorId == null || price < 0 || leadDays <= 0 || now == null) {
            throw new IllegalArgumentException("Valid quote fields are required");
        }
        ProcessingQuote quote = new ProcessingQuote();
        quote.processingRequestId = requestId;
        quote.processorId = processorId;
        quote.price = price;
        quote.leadDays = leadDays;
        quote.memo = memo;
        quote.status = ProcessingQuoteStatus.SUBMITTED;
        quote.createdAt = now;
        return quote;
    }

    public void select() {
        if (status != ProcessingQuoteStatus.SUBMITTED) throw new IllegalStateException("Quote is not selectable");
        status = ProcessingQuoteStatus.SELECTED;
    }
}
