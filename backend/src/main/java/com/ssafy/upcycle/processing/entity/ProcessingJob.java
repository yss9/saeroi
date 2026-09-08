package com.ssafy.upcycle.processing.entity;

import com.ssafy.upcycle.trade.entity.PaymentMethod;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "processing_job")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessingJob {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "job_code", nullable = false, unique = true, length = 30) private String jobCode;
    @Column(name = "processing_request_id", nullable = false) private Long processingRequestId;
    @Column(name = "processing_quote_id", nullable = false, unique = true) private Long processingQuoteId;
    @Column(name = "brand_id", nullable = false) private Long brandId;
    @Column(name = "processor_id", nullable = false) private Long processorId;
    @Column(name = "brand_wallet", nullable = false, length = 42) private String brandWallet;
    @Column(name = "processor_wallet", nullable = false, length = 42) private String processorWallet;
    @Column(nullable = false) private long fee;
    @Column(name = "loss_quantity", nullable = false) private long lossQuantity;
    /**
     * {@code schema.sql} 이 {@code CHAR(66)} 이라 {@code columnDefinition} 으로 못박는다.
     * {@code length = 66} 만 적으면 Hibernate 가 {@code VARCHAR(66)} 을 기대해
     * {@code ddl-auto: validate} 인 local·운영 프로파일에서 <b>기동 자체가 실패한다.</b>
     * 테스트는 {@code create-drop} 이라 엔티티가 시키는 대로 테이블을 만들어 드러나지 않는다
     * ({@code BlockchainTransaction#txHash} 주석에 같은 사고 기록이 있다).
     */
    @Column(name = "evidence_hash", columnDefinition = "CHAR(66)") private String evidenceHash;
    @Column(name = "manifest_url", length = 500) private String manifestUrl;
    @Enumerated(EnumType.STRING) @Column(name = "payment_method", nullable = false) private PaymentMethod paymentMethod;
    @Column(name = "payment_reference", length = 100) private String paymentReference;
    @Column(name = "payment_amount", nullable = false) private long paymentAmount;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ProcessingJobStatus status;
    @Column(name = "payment_expires_at", nullable = false) private LocalDateTime paymentExpiresAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    public static ProcessingJob create(String code, Long requestId, Long quoteId, Long brandId,
            Long processorId, String brandWallet, String processorWallet, long fee,
            PaymentMethod method, LocalDateTime now) {
        if (code == null || requestId == null || quoteId == null || brandId == null || processorId == null
                || brandId.equals(processorId) || brandWallet == null || processorWallet == null
                || fee < 0 || method == null || now == null) throw new IllegalArgumentException("Valid job fields are required");
        ProcessingJob job = new ProcessingJob();
        job.jobCode = code; job.processingRequestId = requestId; job.processingQuoteId = quoteId;
        job.brandId = brandId; job.processorId = processorId; job.brandWallet = brandWallet;
        job.processorWallet = processorWallet; job.fee = fee; job.paymentMethod = method;
        job.lossQuantity = 0; job.paymentAmount = 0; job.status = ProcessingJobStatus.CREATED;
        job.createdAt = now; job.paymentExpiresAt = now.plusMinutes(15);
        return job;
    }

    public boolean isPaymentDue(LocalDateTime now) {
        return status == ProcessingJobStatus.CREATED && !paymentExpiresAt.isAfter(now);
    }
    public void preparePayment(String tid) {
        if (status != ProcessingJobStatus.CREATED || tid == null || tid.isBlank()) throw new IllegalStateException("Payment cannot be prepared");
        paymentReference = tid;
    }
    public void markPaid(String reference, long amount, LocalDateTime at) {
        if (status != ProcessingJobStatus.CREATED || reference == null || reference.isBlank() || amount != fee
                || at == null || !at.isBefore(paymentExpiresAt)) throw new IllegalStateException("Payment cannot be approved");
        paymentReference = reference; paymentAmount = amount; status = ProcessingJobStatus.PAID;
    }
    public void expire(LocalDateTime now) {
        if (!isPaymentDue(now)) throw new IllegalStateException("Job payment is not due");
        status = ProcessingJobStatus.EXPIRED;
    }
}
