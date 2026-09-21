package com.ssafy.upcycle.processing.entity;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.processing.ProcessingErrorCode;
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

    /**
     * 결제창이 열려 있는 시간. 요청을 만든 순간부터 잰다.
     *
     * <p>{@code public} 인 이유는 <b>가공 요청이 같은 값을 써야 하기 때문</b>이다. 요청과
     * 작업이 각자 기한을 만들면 결제창은 살아 있는데 요청이 먼저 죽는다.
     */
    public static final java.time.Duration PAYMENT_WINDOW = java.time.Duration.ofMinutes(15);

    /**
     * 가공사가 수락을 답해야 하는 기간. 결제가 승인된 순간부터 잰다.
     *
     * <p>사흘이다 — 가공사가 주말을 낀 채로 한 번은 확인할 만한 길이이고, 그 이상은
     * 브랜드의 돈과 소재가 묶인 채로 오래 남는다. 지나면 자동으로 거절 처리되고 환불된다.
     */
    public static final java.time.Duration ACCEPTANCE_WINDOW = java.time.Duration.ofDays(3);
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "job_code", nullable = false, unique = true, length = 30) private String jobCode;
    @Column(name = "processing_request_id", nullable = false) private Long processingRequestId;
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
    /**
     * 가공사가 수락을 답해야 하는 시각. <b>결제가 승인될 때 정해진다</b> — 그 전에는 기다리는
     * 것이 결제이지 수락이 아니므로 {@code null} 이다.
     */
    @Column(name = "accept_expires_at") private LocalDateTime acceptExpiresAt;
    @Column(name = "accepted_at") private LocalDateTime acceptedAt;
    @Column(name = "declined_at") private LocalDateTime declinedAt;
    /**
     * 가공사가 일을 거절한 이유. {@code rejectionReason}(브랜드의 결과 반려 사유)과 컬럼을
     * 나눈다 — 한 칸에 담으면 "거절 사유" 가 일을 안 받은 이유인지 결과가 나쁜 이유인지
     * 응답만 보고 알 수 없다. 무응답으로 자동 거절되면 비어 있다.
     */
    @Column(name = "decline_reason", length = 500) private String declineReason;
    /** 환불이 끝난 시각. 두 번 환불하지 않기 위한 표시이기도 하다. */
    @Column(name = "refunded_at") private LocalDateTime refundedAt;
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

    public static ProcessingJob create(String code, Long requestId, Long brandId,
            Long processorId, String brandWallet, String processorWallet, long fee,
            PaymentMethod method, LocalDateTime now) {
        if (code == null || requestId == null || brandId == null || processorId == null
                || brandId.equals(processorId) || brandWallet == null || processorWallet == null
                || fee < 0 || method == null || now == null) throw new IllegalArgumentException("Valid job fields are required");
        ProcessingJob job = new ProcessingJob();
        job.jobCode = code; job.processingRequestId = requestId;
        job.brandId = brandId; job.processorId = processorId; job.brandWallet = brandWallet;
        job.processorWallet = processorWallet; job.fee = fee; job.paymentMethod = method;
        job.lossQuantity = 0; job.paymentAmount = 0; job.resultRevision = 0;
        job.status = ProcessingJobStatus.CREATED;
        job.createdAt = now; job.paymentExpiresAt = now.plus(PAYMENT_WINDOW);
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
        markPaid(reference, amount, at, null);
    }

    /**
     * 결제를 승인하고 <b>같은 자리에서 수락 기한을 건다</b>.
     *
     * <p>둘을 나누면 결제만 되고 기한이 비어 있는 작업이 생기는데, 그 작업은 만료 배치가
     * 찾지 못해 가공사가 영영 답하지 않아도 브랜드의 돈이 묶인 채로 남는다.
     */
    public void markPaid(String reference, long amount, LocalDateTime at,
            LocalDateTime acceptDeadline) {
        if (status != ProcessingJobStatus.CREATED || reference == null || reference.isBlank() || amount != fee
                || at == null || !at.isBefore(paymentExpiresAt)) throw new IllegalStateException("Payment cannot be approved");
        if (acceptDeadline != null && !acceptDeadline.isAfter(at))
            throw new IllegalArgumentException("Acceptance deadline must be in the future");
        paymentReference = reference; paymentAmount = amount; status = ProcessingJobStatus.PAID;
        acceptExpiresAt = acceptDeadline;
    }
    public void expire(LocalDateTime now) {
        if (!isPaymentDue(now)) throw new IllegalStateException("Job payment is not due");
        status = ProcessingJobStatus.EXPIRED;
    }

    /**
     * 가공사가 일을 받았다. <b>결제가 끝난 작업만</b> 수락될 수 있다.
     *
     * <p>수락 기한이 지났더라도 가공사가 먼저 누르면 받아 준다 — 만료 배치가 아직 돌지 않은
     * 몇 분 사이에 들어온 수락을 거절하면, 가공사에게는 "방금 눌렀는데 왜 안 되냐" 가 된다.
     * 배치가 이미 {@code DECLINED} 로 옮겼다면 상태 검사에서 걸린다.
     */
    public void accept(LocalDateTime at) {
        requireState(ProcessingJobStatus.PAID);
        this.acceptedAt = requireTime(at);
        this.status = ProcessingJobStatus.ACCEPTED;
    }

    /**
     * 아직 소재가 오가기 전인가. 취소·거절·만료로 끝낼 수 있는 구간이다.
     *
     * <p>발송 이후에는 이 경로로 끝내지 않는다 — 소재가 이미 가공사에게 가 있으므로
     * 돌려받는 절차(반송)가 먼저다.
     */
    public boolean isBeforeStart() {
        return status == ProcessingJobStatus.CREATED || status == ProcessingJobStatus.PAID;
    }

    /**
     * 시작 전에 끝난다. 브랜드의 취소, 가공사의 거절, 기한 경과가 모두 여기로 온다.
     *
     * <p>셋을 한 메서드로 묶은 이유는 <b>하는 일이 같아서</b>다 — 상태를 종료값으로 옮기고
     * 시각을 적는다. 다른 것은 어느 값으로 가느냐뿐이고, 그 판단은 요청 쪽에서 이미 났다.
     * 세 메서드로 나누면 환불·잠금 해제를 세 번 적게 된다.
     */
    public void terminateBeforeStart(ProcessingJobStatus target, String reason, LocalDateTime at) {
        if (!isBeforeStart()) {
            throw new IllegalStateException(
                    "Only a job before shipment may be terminated but was " + status);
        }
        if (target != ProcessingJobStatus.DECLINED && target != ProcessingJobStatus.EXPIRED
                && target != ProcessingJobStatus.CANCELED) {
            throw new IllegalArgumentException("Not a pre-start terminal job status");
        }
        this.declineReason = normalizeOptional(reason, 500);
        this.declinedAt = requireTime(at);
        this.status = target;
    }


    /** 환불이 끝났다. 이미 기록돼 있으면 아무 일도 하지 않는다 — 두 번 돌려주지 않는다. */
    public void markRefunded(LocalDateTime at) {
        if (refundedAt != null) {
            return;
        }
        this.refundedAt = requireTime(at);
    }

    /** 돌려줄 돈이 남아 있는가. 결제되지 않았거나 이미 환불했으면 아니다. */
    public boolean isRefundable() {
        return paymentAmount > 0 && refundedAt == null;
    }

    /** 가공사가 답하지 않은 채 기한이 지났는가. */
    public boolean isAcceptanceDue(LocalDateTime now) {
        return status == ProcessingJobStatus.PAID
                && acceptExpiresAt != null
                && !acceptExpiresAt.isAfter(now);
    }

    public void ship(String carrier, String trackingNumber, LocalDateTime at) {
        // 결제만으로는 보내지 않는다. 가공사가 받겠다고 답한 뒤여야 한다.
        requireState(ProcessingJobStatus.ACCEPTED);
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

    /**
     * 순서가 어긋난 전이를 막는다.
     *
     * <p><b>도메인 오류로 던진다.</b> 예전에는 raw {@code IllegalStateException} 이라
     * catch-all 로 떨어져 <b>500</b> 이 나갔다 — 11개 전이가 모두 이 메서드를 쓰므로
     * 순서를 어긋나게 부르는 호출이 전부 500 이었다 (2026-09-21 확인: RECEIVED 가 아닌
     * 작업에 {@code PATCH /start} → 500 {@code IllegalStateException}).
     *
     * <p>클라이언트가 이미 지난 단계를 다시 누른 것이지 서버 결함이 아니므로 409 가 맞다.
     * 거래·주문은 서비스 층에서 이미 그렇게 답하고 있어 기준을 맞춘다.
     */
    private void requireState(ProcessingJobStatus expected) {
        if (status != expected) {
            throw new BusinessException(ProcessingErrorCode.PROCESSING_INVALID_STATE);
        }
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
