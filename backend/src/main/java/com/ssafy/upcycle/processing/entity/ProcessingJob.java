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
    @Column(name = "chain_job_id", unique = true) private Long chainJobId;
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
    @Column(name = "carrier", length = 50) private String carrier;
    @Column(name = "tracking_number", length = 100) private String trackingNumber;
    @Column(name = "receive_tx_hash", unique = true, columnDefinition = "CHAR(66)") private String receiveTxHash;
    @Column(name = "receive_block_number") private Long receiveBlockNumber;
    @Column(name = "return_carrier", length = 50) private String returnCarrier;
    @Column(name = "return_tracking_number", length = 100) private String returnTrackingNumber;
    @Column(name = "return_shipping_evidence_hash", columnDefinition = "CHAR(66)") private String returnShippingEvidenceHash;
    @Column(name = "return_ship_tx_hash", unique = true, columnDefinition = "CHAR(66)") private String returnShipTxHash;
    @Column(name = "return_ship_block_number") private Long returnShipBlockNumber;
    @Column(name = "return_receive_tx_hash", unique = true, columnDefinition = "CHAR(66)") private String returnReceiveTxHash;
    @Column(name = "return_receive_block_number") private Long returnReceiveBlockNumber;
    @Column(name = "result_revision", nullable = false) private int resultRevision;
    @Column(name = "rejection_reason", length = 500) private String rejectionReason;
    @Column(name = "result_submit_signature", length = 132) private String resultSubmitSignature;
    @Column(name = "approval_signature", length = 132) private String approvalSignature;
    @Column(name = "rejection_signature", length = 132) private String rejectionSignature;
    @Column(name = "shipped_at") private LocalDateTime shippedAt;
    @Column(name = "received_at") private LocalDateTime receivedAt;
    @Column(name = "started_at") private LocalDateTime startedAt;
    @Column(name = "result_drafted_at") private LocalDateTime resultDraftedAt;
    @Column(name = "result_submitted_at") private LocalDateTime resultSubmittedAt;
    @Column(name = "rejected_at") private LocalDateTime rejectedAt;
    @Column(name = "return_shipped_at") private LocalDateTime returnShippedAt;
    @Column(name = "completed_at") private LocalDateTime completedAt;
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
        job.lossQuantity = 0; job.paymentAmount = 0; job.resultRevision = 0;
        job.status = ProcessingJobStatus.CREATED;
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

    public void ship(String carrier, String trackingNumber, LocalDateTime at) {
        requireState(ProcessingJobStatus.PAID);
        this.carrier = normalizeOptional(carrier, 50);
        this.trackingNumber = normalizeOptional(trackingNumber, 100);
        requireTime(at);
        this.shippedAt = at;
        this.status = ProcessingJobStatus.SHIPPED;
    }

    /** 이전 API와의 호환용 전이. 배송사 정보가 없는 요청도 같은 상태 머신을 사용한다. */
    public void ship(LocalDateTime at) {
        ship(null, null, at);
    }

    public void receive(LocalDateTime at) {
        requireState(ProcessingJobStatus.SHIPPED);
        this.receivedAt = requireTime(at);
        this.status = ProcessingJobStatus.RECEIVED;
    }

    public void linkChainJob(long onChainId) {
        if (onChainId <= 0) throw new IllegalArgumentException("Chain job id must be positive");
        if (chainJobId != null && chainJobId != onChainId) throw new IllegalStateException("Chain job is already linked");
        chainJobId = onChainId;
    }

    public void receiveOnChain(String txHash, Long blockNumber, LocalDateTime at) {
        if (receiveTxHash != null) {
            if (receiveTxHash.equalsIgnoreCase(txHash)) return;
            throw new IllegalStateException("Receipt transaction is already linked");
        }
        receive(at);
        receiveTxHash = requireTxHash(txHash);
        receiveBlockNumber = requireBlock(blockNumber);
    }

    public void start(LocalDateTime at) {
        requireState(ProcessingJobStatus.RECEIVED);
        this.startedAt = requireTime(at);
        this.status = ProcessingJobStatus.PROCESSING;
    }

    /** 이전 API와의 호환용 전이. */
    public void startProcessing() {
        start(LocalDateTime.now());
    }

    public void saveResultDraft(long lossQuantity, LocalDateTime at) {
        if (status != ProcessingJobStatus.PROCESSING && status != ProcessingJobStatus.REJECTED) {
            throw new IllegalStateException("Result draft cannot be saved");
        }
        if (lossQuantity < 0) throw new IllegalArgumentException("Loss quantity cannot be negative");
        this.lossQuantity = lossQuantity;
        this.resultRevision++;
        this.resultDraftedAt = requireTime(at);
        this.rejectionReason = null;
        this.rejectionSignature = null;
        this.rejectedAt = null;
    }

    public void submitResult(String signature, LocalDateTime at) {
        if ((status != ProcessingJobStatus.PROCESSING && status != ProcessingJobStatus.REJECTED)
                || resultRevision <= 0) throw new IllegalStateException("Result cannot be submitted");
        this.resultSubmitSignature = requireSignature(signature);
        this.resultSubmittedAt = requireTime(at);
        this.status = ProcessingJobStatus.RESULT_SUBMITTED;
    }

    /** 이전 결과 제출 API를 최종 상태 모델에 연결한다. */
    public void submitResult(long inputQuantity, long outputQuantity, String evidenceHash,
            String manifestUrl, LocalDateTime at) {
        requireState(ProcessingJobStatus.PROCESSING);
        if (inputQuantity <= 0 || outputQuantity <= 0 || outputQuantity > inputQuantity)
            throw new IllegalArgumentException("Output quantity must be positive and not exceed input quantity");
        this.lossQuantity = inputQuantity - outputQuantity;
        this.evidenceHash = normalizeOptional(evidenceHash, 66);
        this.manifestUrl = normalizeOptional(manifestUrl, 500);
        this.resultRevision++;
        this.resultDraftedAt = requireTime(at);
        this.resultSubmittedAt = at;
        this.status = ProcessingJobStatus.RESULT_SUBMITTED;
    }

    public void reject(String reason, String signature, LocalDateTime at) {
        requireState(ProcessingJobStatus.RESULT_SUBMITTED);
        if (reason == null || reason.isBlank() || reason.trim().length() > 500)
            throw new IllegalArgumentException("Rejection reason is required");
        this.rejectionReason = reason.trim();
        this.rejectionSignature = requireSignature(signature);
        this.rejectedAt = requireTime(at);
        this.status = ProcessingJobStatus.REJECTED;
    }

    public void shipReturn(String carrier, String trackingNumber, String evidenceHash,
            String txHash, Long blockNumber, LocalDateTime at) {
        requireState(ProcessingJobStatus.RESULT_SUBMITTED);
        this.returnCarrier = normalizeOptional(carrier, 50);
        this.returnTrackingNumber = normalizeOptional(trackingNumber, 100);
        this.returnShippingEvidenceHash = requireHash(evidenceHash, "Return shipping evidence hash is required");
        this.returnShipTxHash = requireTxHash(txHash);
        this.returnShipBlockNumber = requireBlock(blockNumber);
        this.returnShippedAt = requireTime(at);
        this.status = ProcessingJobStatus.RETURN_SHIPPED;
    }

    public void confirmReturn(String txHash, Long blockNumber, String signature, LocalDateTime at) {
        requireState(ProcessingJobStatus.RETURN_SHIPPED);
        this.returnReceiveTxHash = requireTxHash(txHash);
        this.returnReceiveBlockNumber = requireBlock(blockNumber);
        this.approvalSignature = signature;
        this.completedAt = requireTime(at);
        this.status = ProcessingJobStatus.CONFIRMED;
    }

    public void cancelAfterRejectedReturn(String carrier, String trackingNumber, String evidenceHash,
            String txHash, Long blockNumber, LocalDateTime at) {
        requireState(ProcessingJobStatus.REJECTED);
        this.returnCarrier = normalizeOptional(carrier, 50);
        this.returnTrackingNumber = normalizeOptional(trackingNumber, 100);
        this.returnShippingEvidenceHash = requireHash(evidenceHash, "Return shipping evidence hash is required");
        this.returnShipTxHash = requireTxHash(txHash);
        this.returnShipBlockNumber = requireBlock(blockNumber);
        this.returnShippedAt = requireTime(at);
        this.completedAt = at;
        this.status = ProcessingJobStatus.CANCELED;
    }

    public void approve(String signature, LocalDateTime at) {
        requireState(ProcessingJobStatus.RESULT_SUBMITTED);
        this.approvalSignature = requireSignature(signature);
        this.completedAt = requireTime(at);
        this.status = ProcessingJobStatus.CONFIRMED;
    }

    /** 이전 결과 승인 API를 최종 상태 모델에 연결한다. */
    public void confirmResult(LocalDateTime at) {
        requireState(ProcessingJobStatus.RESULT_SUBMITTED);
        this.completedAt = requireTime(at);
        this.status = ProcessingJobStatus.CONFIRMED;
    }

    public LocalDateTime getProcessorSubmittedAt() {
        return resultSubmittedAt;
    }

    public LocalDateTime getBrandReviewedAt() {
        return completedAt;
    }

    private void requireState(ProcessingJobStatus expected) {
        if (status != expected) throw new IllegalStateException("Expected job state " + expected);
    }

    private static LocalDateTime requireTime(LocalDateTime at) {
        if (at == null) throw new IllegalArgumentException("Transition time is required");
        return at;
    }

    private static String requireSignature(String signature) {
        if (signature == null || signature.isBlank() || signature.length() > 132)
            throw new IllegalArgumentException("Signature is required");
        return signature;
    }

    private static String requireTxHash(String value) {
        if (value == null || !value.matches("^0x[0-9a-fA-F]{64}$"))
            throw new IllegalArgumentException("Confirmed transaction hash is required");
        return value.toLowerCase();
    }

    private static String requireHash(String value, String message) {
        if (value == null || !value.matches("^0x[0-9a-fA-F]{64}$"))
            throw new IllegalArgumentException(message);
        return value.toLowerCase();
    }

    private static Long requireBlock(Long value) {
        if (value == null || value < 0) throw new IllegalArgumentException("Block number is required");
        return value;
    }

    private static String normalizeOptional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException("Shipping field is too long");
        return normalized;
    }
}
