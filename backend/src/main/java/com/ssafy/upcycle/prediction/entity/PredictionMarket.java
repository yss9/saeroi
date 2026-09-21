package com.ssafy.upcycle.prediction.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@Table(name = "prediction_market")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class PredictionMarket {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") private Long id;
    @Column(name = "chain_market_id", nullable = false, unique = true) private long chainMarketId;
    @Column(name = "production_batch_id", nullable = false, unique = true) private Long productionBatchId;
    @Column(name = "chain_batch_id", nullable = false, unique = true) private long chainBatchId;
    @Column(name = "product_model_id", nullable = false) private Long productModelId;
    @Column(name = "product_name", nullable = false, length = 200) private String productName;
    @Column(name = "product_thumbnail_url", length = 500) private String productThumbnailUrl;
    @Column(name = "brand_id", nullable = false) private Long brandId;
    @Column(name = "brand_wallet", nullable = false, length = 42) private String brandWallet;
    @Column(name = "unit_price", nullable = false) private long unitPrice;
    @Column(name = "production_quantity", nullable = false) private int productionQuantity;
    @Column(name = "betting_ends_at", nullable = false) private LocalDateTime bettingEndsAt;
    @Column(name = "sales_starts_at", nullable = false) private LocalDateTime salesStartsAt;
    @Column(name = "sales_ends_at", nullable = false) private LocalDateTime salesEndsAt;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false) private PredictionMarketStatus status;
    @Column(name = "sold_quantity") private Integer soldQuantity;
    @Column(name = "winning_bucket_index") private Integer winningBucketIndex;
    @Column(name = "result_hash", columnDefinition = "CHAR(66)") private String resultHash;
    @Column(name = "creation_tx_hash", nullable = false, columnDefinition = "CHAR(66)") private String creationTxHash;
    @Column(name = "resolution_tx_hash", columnDefinition = "CHAR(66)") private String resolutionTxHash;
    @CreatedDate @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "resolved_at") private LocalDateTime resolvedAt;

    public static PredictionMarket create(
            long chainMarketId, Long batchId, long chainBatchId, Long productModelId,
            String productName, String thumbnail, Long brandId, String brandWallet,
            long unitPrice, int productionQuantity, LocalDateTime bettingEndsAt,
            LocalDateTime salesStartsAt, LocalDateTime salesEndsAt, String creationTxHash) {
        PredictionMarket value = new PredictionMarket();
        value.chainMarketId = chainMarketId; value.productionBatchId = batchId;
        value.chainBatchId = chainBatchId; value.productModelId = productModelId;
        value.productName = productName; value.productThumbnailUrl = thumbnail;
        value.brandId = brandId; value.brandWallet = brandWallet.toLowerCase();
        value.unitPrice = unitPrice; value.productionQuantity = productionQuantity;
        value.bettingEndsAt = bettingEndsAt; value.salesStartsAt = salesStartsAt;
        value.salesEndsAt = salesEndsAt; value.status = PredictionMarketStatus.OPEN;
        value.creationTxHash = creationTxHash;
        return value;
    }

    public void startResolving() { if (status == PredictionMarketStatus.OPEN) status = PredictionMarketStatus.RESOLVING; }
    public void reopenAfterFailure() { if (status == PredictionMarketStatus.RESOLVING) status = PredictionMarketStatus.OPEN; }
    /**
     * 시장을 무효로 돌린다. 온체인 {@code cancelMarket} 이 확정된 뒤에만 부른다.
     *
     * <p>확정과 무효화는 둘 다 "이 시장을 끝낸 트랜잭션" 이라 {@code resolution_tx_hash} 한 칸을
     * 같이 쓴다. 어느 쪽으로 끝났는지는 {@code status} 가 말하므로 칼럼을 더 두지 않는다.
     *
     * <p>{@code RESOLVING} 중인 시장은 막는다 — 오라클이 이미 결과를 제출하는 중이라면
     * 체인에서 둘 중 하나는 revert 되고, 그 사이 DB 만 바뀌면 두 기록이 어긋난다.
     */
    public void cancel(String txHash, LocalDateTime at) {
        if (status != PredictionMarketStatus.OPEN) throw new IllegalStateException("market is not open");
        resolutionTxHash = txHash; resolvedAt = at; status = PredictionMarketStatus.CANCELED;
    }

    public void resolve(int sold, int bucket, String hash, String txHash, LocalDateTime at) {
        if (status != PredictionMarketStatus.RESOLVING) throw new IllegalStateException("market is not resolving");
        soldQuantity = sold; winningBucketIndex = bucket; resultHash = hash;
        resolutionTxHash = txHash; resolvedAt = at; status = PredictionMarketStatus.RESOLVED;
    }
}
