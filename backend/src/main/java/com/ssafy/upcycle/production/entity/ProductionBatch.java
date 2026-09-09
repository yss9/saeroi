package com.ssafy.upcycle.production.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 한 번의 생산. "RE:BAG 1차 생산 50개" 에 해당한다.
 *
 * <p><b>제품과 소재를 잇는 다리다.</b> 위로는 {@code product_model} 을 가리키고 아래로는
 * {@link ProductionInput} 이 소재를 가리킨다. 이 행이 확정되어야 {@code product_item} 이
 * 만들어지므로, 배치가 없으면 팔 물건도 없다.
 *
 * <h2>업사이클 비율을 스냅샷으로 저장한다</h2>
 *
 * <p>ERD 가 못 박은 규칙이다. 매번 재계산하면 나중에 소재 데이터가 바뀔 때 <b>이미 팔린
 * 제품의 비율이 소급해 변한다.</b> 그건 이력이 아니다. 그래서 확정 시점의 값을 세 수량
 * 컬럼과 함께 굳혀 둔다.
 *
 * <p>계산식은 {@link #confirm} 이 아니라 확정 서비스가 갖는다 — 비율을 내려면
 * {@code material.root_origin} 을 봐야 하는데 엔티티는 소재를 모른다.
 *
 * <h2>서명 지갑을 스냅샷으로 들고 있다</h2>
 *
 * <p>{@code producer_wallet} 은 확정 시점에 실제로 서명한 주소다. {@code user_wallet} 을
 * 나중에 다시 읽으면 지갑을 교체한 뒤에는 <b>다른 주소가 나온다</b> — 온체인 기록과 DB 가
 * 어긋나고, 그 어긋남은 검증할 때만 드러난다.
 *
 * <p>제품 모델·생산자를 연관관계가 아니라 FK 값으로 들고 있는 이유는 Material 도메인과 같다.
 */
@Getter
@Entity
@Table(name = "production_batch")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ProductionBatch {

    /** {@code batch_code} 컬럼 길이. 임시 코드도 이 안에 들어가야 한다. */
    public static final int BATCH_CODE_MAX_LENGTH = 20;

    /**
     * 아직 정식 코드가 붙지 않은 배치의 코드 접두어.
     *
     * <p>{@code batch_code} 가 {@code NOT NULL} UNIQUE 라 INSERT 시점에 값이 필요한데 정식
     * 코드는 저장 후 확정되는 {@code id} 에서 파생한다. 소재 등록이 같은 문제를 같은
     * 방식으로 푼다 ({@code Material.TEMPORARY_CODE_PREFIX}).
     */
    public static final String TEMPORARY_CODE_PREFIX = "TMP-";

    private static final int BATCH_NAME_MAX_LENGTH = 200;
    private static final int WALLET_ADDRESS_LENGTH = 42;
    private static final int EVIDENCE_HASH_LENGTH = 66;
    private static final int MANIFEST_URL_MAX_LENGTH = 500;

    /** {@code upcycle_ratio DECIMAL(5,2)} 의 소수 자릿수. */
    private static final int RATIO_SCALE = 2;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사람이 읽는 식별자. {@code PB-000001}. */
    @Column(name = "batch_code", nullable = false, unique = true, length = BATCH_CODE_MAX_LENGTH)
    private String batchCode;

    @Column(name = "batch_name", nullable = false, length = BATCH_NAME_MAX_LENGTH)
    private String batchName;

    /** 무엇을 만드는가. product_model FK 다. */
    @Column(name = "product_model_id", nullable = false)
    private Long productModelId;

    /** 몇 개를 만드는가. 확정되면 이 수만큼 {@code product_item} 이 생긴다. */
    @Column(name = "produced_quantity", nullable = false)
    private int producedQuantity;

    /** 만드는 브랜드. users FK 다. */
    @Column(name = "producer_id", nullable = false)
    private Long producerId;

    /** 서명 시점 스냅샷. 지갑을 바꿔도 이 값은 변하지 않는다. */
    @Column(name = "producer_wallet", nullable = false, length = WALLET_ADDRESS_LENGTH)
    private String producerWallet;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProductionBatchStatus status;

    /** 제품에 실제로 쓰인 총량 (g). {@code virgin + upcycled} 와 같아야 한다. */
    @Column(name = "total_used_quantity", nullable = false)
    private long totalUsedQuantity;

    /** {@code root_origin = VIRGIN} 소재 사용량 (g). */
    @Column(name = "virgin_used_quantity", nullable = false)
    private long virginUsedQuantity;

    /** {@code root_origin} 이 WASTE·RECOVERED 인 소재 사용량 (g). */
    @Column(name = "upcycled_used_quantity", nullable = false)
    private long upcycledUsedQuantity;

    /** 확정 시점의 업사이클 비율 (%). 스냅샷이며 다시 계산하지 않는다. */
    @Column(name = "upcycle_ratio", nullable = false, precision = 5, scale = RATIO_SCALE)
    private BigDecimal upcycleRatio;

    /** Manifest 해시. 온체인 대조용이라 확정 전에는 없다. */
    @Column(name = "evidence_hash", columnDefinition = "CHAR(66)")
    private String evidenceHash;

    @Column(name = "manifest_url", length = MANIFEST_URL_MAX_LENGTH)
    private String manifestUrl;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    private ProductionBatch(
            String batchCode,
            String batchName,
            Long productModelId,
            int producedQuantity,
            Long producerId,
            String producerWallet) {
        this.batchCode = requireText(batchCode, "batch code", BATCH_CODE_MAX_LENGTH);
        this.batchName = requireText(batchName, "batch name", BATCH_NAME_MAX_LENGTH);
        this.productModelId = requireNonNull(productModelId, "product model id");
        if (producedQuantity <= 0) {
            throw new IllegalArgumentException("Production batch produced quantity must be positive");
        }
        this.producedQuantity = producedQuantity;
        this.producerId = requireNonNull(producerId, "producer id");
        this.producerWallet = requireWallet(producerWallet);
        this.status = ProductionBatchStatus.DRAFT;
        this.totalUsedQuantity = 0L;
        this.virginUsedQuantity = 0L;
        this.upcycledUsedQuantity = 0L;
        this.upcycleRatio = BigDecimal.ZERO.setScale(RATIO_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 배치를 연다. <b>{@link ProductionBatchStatus#DRAFT} 로 시작한다.</b>
     *
     * <p>수량 세 컬럼과 비율은 0 이다. 투입이 아직 없으니 그것이 사실이고,
     * 확정 시점에 {@link #confirm} 이 실제 값으로 채운다.
     */
    public static ProductionBatch open(
            String batchCode,
            String batchName,
            Long productModelId,
            int producedQuantity,
            Long producerId,
            String producerWallet) {
        return new ProductionBatch(
                batchCode, batchName, productModelId, producedQuantity, producerId, producerWallet);
    }

    /**
     * 정식 코드를 붙인다. 저장 직후 같은 트랜잭션에서 부른다.
     *
     * <p>이미 정식 코드가 있으면 거부한다 — 코드가 바뀌면 그 코드로 남긴 기록이 무엇을
     * 가리키는지 알 수 없게 된다.
     */
    public void assignCode(String batchCode) {
        String assigned = requireText(batchCode, "batch code", BATCH_CODE_MAX_LENGTH);
        if (!this.batchCode.startsWith(TEMPORARY_CODE_PREFIX)) {
            throw new IllegalStateException(
                    "Production batch code is already assigned and must not change: " + this.batchCode);
        }
        this.batchCode = assigned;
    }

    /**
     * 서명을 시작한다. <b>이 뒤로는 투입을 바꿀 수 없다.</b>
     *
     * <p>이 상태가 없으면 같은 배치를 두 번 확정할 수 있다 — 브랜드 지갑이 서명하는 동안
     * 서버는 기다리는데, 그 창을 상태로 남기지 않으면 두 번째 요청을 막을 근거가 없다.
     */
    public void startSigning() {
        requireStatus(ProductionBatchStatus.DRAFT, "start signing");
        this.status = ProductionBatchStatus.SIGNING;
    }

    /**
     * 배치를 확정한다. <b>되돌릴 수 없다.</b>
     *
     * <p>수량은 계산해서 받는다. 비율을 내려면 {@code material.root_origin} 을 봐야 하는데
     * 엔티티는 소재를 모르기 때문이다. 다만 <b>합이 맞는지는 여기서 확인한다</b> —
     * {@code chk_production_batch_quantities} 를 위반하면 사용자에게 설명할 수 없는 500 이
     * 되고, DB 가 막아 주기 전에 엔티티가 먼저 거절하는 편이 낫다.
     *
     * @param virginUsedQuantity {@code root_origin = VIRGIN} 소재 사용량 (g)
     * @param upcycledUsedQuantity WASTE·RECOVERED 계열 사용량 (g)
     */
    public void confirm(
            long virginUsedQuantity, long upcycledUsedQuantity, LocalDateTime confirmedAt) {
        requireStatus(ProductionBatchStatus.SIGNING, "confirm");
        if (virginUsedQuantity < 0 || upcycledUsedQuantity < 0) {
            throw new IllegalArgumentException("Production batch used quantities must not be negative");
        }
        this.virginUsedQuantity = virginUsedQuantity;
        this.upcycledUsedQuantity = upcycledUsedQuantity;
        this.totalUsedQuantity = virginUsedQuantity + upcycledUsedQuantity;
        this.upcycleRatio = ratioOf(virginUsedQuantity, upcycledUsedQuantity);
        this.status = ProductionBatchStatus.CONFIRMED;
        this.confirmedAt = requireNonNull(confirmedAt, "confirmed at");
    }

    /**
     * 서명이나 전송이 실패했다. 다시 시도할 수 있도록 {@code DRAFT} 로 되돌린다.
     *
     * <p><b>잠금은 풀지 않는다.</b> 실패는 "이 배치를 그만둔다" 가 아니라 "다시 해 본다"
     * 이고, 잠금을 풀면 그 사이에 다른 곳에서 같은 소재를 가져갈 수 있다. 실제로 그만두는
     * 것은 {@link #cancel} 이다.
     */
    public void markFailed() {
        requireStatus(ProductionBatchStatus.SIGNING, "fail");
        this.status = ProductionBatchStatus.FAILED;
    }

    /** 실패한 배치를 다시 작성 가능한 상태로 되돌린다. */
    public void reopen() {
        requireStatus(ProductionBatchStatus.FAILED, "reopen");
        this.status = ProductionBatchStatus.DRAFT;
    }

    /**
     * 배치를 그만둔다. <b>확정된 뒤에는 불가능하다</b> — 소재는 이미 소비됐고 개체는 이미
     * 만들어졌다. 그것을 되돌리는 일은 취소가 아니라 별도의 보정이다.
     */
    public void cancel() {
        if (this.status == ProductionBatchStatus.CONFIRMED) {
            throw new IllegalStateException("Confirmed production batch cannot be canceled");
        }
        if (this.status == ProductionBatchStatus.CANCELED) {
            throw new IllegalStateException("Production batch is already canceled");
        }
        this.status = ProductionBatchStatus.CANCELED;
    }

    /** 온체인 기록 결과를 붙인다. 확정된 배치에만 의미가 있다. */
    public void linkManifest(String evidenceHash, String manifestUrl) {
        this.evidenceHash = requireHash(evidenceHash);
        this.manifestUrl = requireOptionalText(manifestUrl, "manifest url", MANIFEST_URL_MAX_LENGTH);
    }

    /** 투입을 바꿀 수 있는 상태인가. {@code DRAFT} 뿐이다. */
    public boolean isEditable() {
        return this.status == ProductionBatchStatus.DRAFT;
    }

    public boolean isConfirmed() {
        return this.status == ProductionBatchStatus.CONFIRMED;
    }

    /**
     * {@code upcycled / (virgin + upcycled) × 100}.
     *
     * <p>투입이 하나도 없으면 {@code 0} 이다 — 0 으로 나누지 않는다. 비율을 "알 수 없음"
     * 으로 두고 싶어도 컬럼이 {@code NOT NULL} 이라 그럴 수 없고, 확정 전에는 이 값을
     * 아무도 읽지 않는다.
     *
     * <p>{@code DECIMAL(5,2)} 에 맞춰 소수 둘째 자리에서 반올림한다. {@code double} 로
     * 계산하면 33.33 이 33.329999… 로 저장되는 일이 생긴다.
     */
    private static BigDecimal ratioOf(long virgin, long upcycled) {
        long total = virgin + upcycled;
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(RATIO_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(upcycled)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(total), RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private void requireStatus(ProductionBatchStatus expected, String action) {
        if (this.status != expected) {
            throw new IllegalStateException(
                    "Production batch must be " + expected + " to " + action + " but was " + this.status);
        }
    }

    private static String requireWallet(String walletAddress) {
        String wallet = requireText(walletAddress, "producer wallet", WALLET_ADDRESS_LENGTH);
        if (wallet.length() != WALLET_ADDRESS_LENGTH) {
            throw new IllegalArgumentException(
                    "Production batch producer wallet must be " + WALLET_ADDRESS_LENGTH + " characters");
        }
        return wallet;
    }

    private static String requireHash(String hash) {
        String value = requireText(hash, "evidence hash", EVIDENCE_HASH_LENGTH);
        if (value.length() != EVIDENCE_HASH_LENGTH) {
            throw new IllegalArgumentException(
                    "Production batch evidence hash must be " + EVIDENCE_HASH_LENGTH + " characters");
        }
        return value;
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Production batch " + field + " must not be null");
        }
        return value;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Production batch " + field + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Production batch " + field + " must not exceed " + maxLength + " characters");
        }
        return value;
    }

    private static String requireOptionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireText(value, field, maxLength);
    }
}
