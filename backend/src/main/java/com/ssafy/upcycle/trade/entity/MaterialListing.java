package com.ssafy.upcycle.trade.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 소재 판매 등록. 부분 구매를 지원하므로 등록 수량과 잔여 수량을 따로 들고 있다.
 *
 * <p><b>잔여 수량과 잠금은 서로 다른 시점에 줄어든다.</b> 정책 7.2 기준으로
 * {@code available_quantity} 는 <b>구매 시점</b>에 줄어 다른 구매자가 같은 수량을 사지
 * 못하게 막고, 잠금은 그대로 유지되다가 <b>구매자 수령 확인 시점</b>에 소비된다.
 * 두 값이 같은 사실을 다르게 보는 것이 아니라, 각각 "더 팔 수 있는가"와
 * "판매자가 다른 데 쓸 수 있는가"를 답한다.
 *
 * <p>{@code material_lock_id} 는 판매 등록 시 생성된 MARKETPLACE 잠금을 가리킨다.
 * Escrow 를 두지 않으므로 배송 중에도 이 잠금 하나가 계속 유지된다.
 */
@Getter
@Entity
@Table(name = "material_listing")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class MaterialListing {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 온체인 Listing 번호. 체인 등록 전에는 비어 있다. */
    @Column(name = "chain_listing_id", unique = true)
    private Long chainListingId;

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    /** 등록 수량 (gram). */
    @Column(name = "total_quantity", nullable = false)
    private long totalQuantity;

    /** 아직 팔 수 있는 수량 (gram). */
    @Column(name = "available_quantity", nullable = false)
    private long availableQuantity;

    /** kg 당 가격 (원). 수량은 gram 이므로 정산 시 단위를 맞춰야 한다. */
    @Column(name = "price_per_kg", nullable = false)
    private long pricePerKg;

    /** 등록 시 생성된 MARKETPLACE 잠금. */
    @Column(name = "material_lock_id", nullable = false)
    private Long materialLockId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ListingStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private MaterialListing(
            Long materialId,
            Long sellerId,
            long totalQuantity,
            long pricePerKg,
            Long materialLockId) {
        if (materialId == null) {
            throw new IllegalArgumentException("Material listing material id must not be null");
        }
        if (sellerId == null) {
            throw new IllegalArgumentException("Material listing seller id must not be null");
        }
        if (materialLockId == null) {
            throw new IllegalArgumentException("Material listing lock id must not be null");
        }
        if (totalQuantity <= 0) {
            throw new IllegalArgumentException("Material listing total quantity must be positive");
        }
        if (pricePerKg < 0) {
            throw new IllegalArgumentException("Material listing price must not be negative");
        }
        this.materialId = materialId;
        this.sellerId = sellerId;
        this.totalQuantity = totalQuantity;
        this.availableQuantity = totalQuantity;
        this.pricePerKg = pricePerKg;
        this.materialLockId = materialLockId;
        this.status = ListingStatus.ON_SALE;
    }

    /** 판매를 등록한다. 잔여 수량은 등록 수량과 같게 시작한다. */
    public static MaterialListing open(
            Long materialId,
            Long sellerId,
            long totalQuantity,
            long pricePerKg,
            Long materialLockId) {
        return new MaterialListing(materialId, sellerId, totalQuantity, pricePerKg, materialLockId);
    }

    /**
     * 구매가 성사되어 더 팔 수 있는 수량이 줄어든다.
     *
     * <p>잔여가 0 이 되면 {@link ListingStatus#SOLD_OUT} 으로 넘긴다. 잠금은 여기서
     * 건드리지 않는다 — 배송 중에도 유지되어야 하고, 소비 시점은 수령 확인이다.
     */
    public void reduceAvailable(long quantity) {
        if (status != ListingStatus.ON_SALE) {
            throw new IllegalStateException(
                    "Material listing can only be sold while ON_SALE but was " + status);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Material listing sold quantity must be positive");
        }
        if (this.availableQuantity < quantity) {
            throw new IllegalArgumentException(
                    "Material listing has only " + this.availableQuantity
                            + "g left but " + quantity + "g was requested");
        }
        this.availableQuantity -= quantity;
        if (this.availableQuantity == 0) {
            this.status = ListingStatus.SOLD_OUT;
        }
    }

    /** 거래가 취소되어 잔여 수량이 되돌아온다. 소진으로 닫혔던 판매도 다시 열린다. */
    public void restoreAvailable(long quantity) {
        if (status == ListingStatus.CANCELED) {
            throw new IllegalStateException("Canceled material listing cannot restore quantity");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Material listing restored quantity must be positive");
        }
        if (this.availableQuantity + quantity > this.totalQuantity) {
            throw new IllegalArgumentException(
                    "Material listing cannot restore beyond its total quantity");
        }
        this.availableQuantity += quantity;
        this.status = ListingStatus.ON_SALE;
    }
}
