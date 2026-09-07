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
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Supplier → Brand 소재 거래 한 건.
 *
 * <p><b>소유권은 배송 시점이 아니라 수령 확인 시점에 넘어간다.</b> 판매자가 물건을
 * 보냈다는 사실만으로는 받았다는 보장이 없다. 배송 중 수량은 MARKETPLACE 잠금 상태로
 * 판매자 잔고에 남아 있되 다른 곳에 팔 수 없다.
 *
 * <p>{@code buyer_signed_at} 과 {@code received_at} 은 다른 값이다. 전자는 구매자가
 * Wallet 으로 서명한 시각이고 후자는 수령 확인 시각이다. 정책 09.1 의 확정 조건이
 * 둘을 따로 요구하며, 검증 단계 {@code TRANSACTION_VERIFIED} 의 판정 근거는 서명 쪽이다.
 *
 * <p>지갑 주소를 거래 시점 스냅샷으로 들고 있다. 나중에 지갑을 교체해도 이 거래가
 * 어느 주소로 이루어졌는지는 바뀌면 안 된다.
 */
@Getter
@Entity
@Table(name = "material_trade")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class MaterialTrade {

    private static final Pattern WALLET_ADDRESS_PATTERN =
            Pattern.compile("^0x[0-9a-fA-F]{40}$");
    private static final int TRADE_CODE_MAX_LENGTH = 30;

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 온체인 주문 번호. 체인 등록 전에는 비어 있다. */
    @Column(name = "chain_trade_id", unique = true)
    private Long chainTradeId;

    @Column(name = "trade_code", nullable = false, length = TRADE_CODE_MAX_LENGTH, unique = true)
    private String tradeCode;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    /** 거래 시점 스냅샷. 지갑을 교체해도 이 값은 바뀌지 않는다. */
    @Column(name = "seller_wallet", nullable = false, length = 42)
    private String sellerWallet;

    @Column(name = "buyer_wallet", nullable = false, length = 42)
    private String buyerWallet;

    /** 거래 수량 (gram). */
    @Column(name = "quantity", nullable = false)
    private long quantity;

    /** 총 거래 금액 (원). */
    @Column(name = "total_price", nullable = false)
    private long totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    /** PG 승인 tid. 결제 전에는 비어 있다. */
    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    /** 실제 승인된 금액 (원). */
    @Column(name = "payment_amount", nullable = false)
    private long paymentAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TradeStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    /** 구매자 Wallet 서명 시각. 수령 확인 시각과 별개다. */
    @Column(name = "buyer_signed_at")
    private LocalDateTime buyerSignedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    private MaterialTrade(
            String tradeCode,
            Long listingId,
            Long materialId,
            Long sellerId,
            Long buyerId,
            String sellerWallet,
            String buyerWallet,
            long quantity,
            long totalPrice,
            PaymentMethod paymentMethod) {
        this.tradeCode = requireText(tradeCode, "trade code", TRADE_CODE_MAX_LENGTH);
        this.listingId = requireNonNull(listingId, "listing id");
        this.materialId = requireNonNull(materialId, "material id");
        this.sellerId = requireNonNull(sellerId, "seller id");
        this.buyerId = requireNonNull(buyerId, "buyer id");
        if (sellerId.equals(buyerId)) {
            throw new IllegalArgumentException("Material trade seller and buyer must differ");
        }
        this.sellerWallet = requireWalletAddress(sellerWallet, "seller wallet");
        this.buyerWallet = requireWalletAddress(buyerWallet, "buyer wallet");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Material trade quantity must be positive");
        }
        if (totalPrice < 0) {
            throw new IllegalArgumentException("Material trade total price must not be negative");
        }
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.paymentMethod = requireNonNull(paymentMethod, "payment method");
        this.paymentAmount = 0L;
        this.status = TradeStatus.CREATED;
    }

    /** 구매 요청이 만들어진다. 결제 전이므로 CREATED 로 시작한다. */
    public static MaterialTrade create(
            String tradeCode,
            Long listingId,
            Long materialId,
            Long sellerId,
            Long buyerId,
            String sellerWallet,
            String buyerWallet,
            long quantity,
            long totalPrice,
            PaymentMethod paymentMethod) {
        return new MaterialTrade(
                tradeCode,
                listingId,
                materialId,
                sellerId,
                buyerId,
                sellerWallet,
                buyerWallet,
                quantity,
                totalPrice,
                paymentMethod);
    }

    /** 결제가 승인되었다. PG 승인 tid 와 실제 승인 금액을 남긴다. */
    public void markPaid(String paymentReference, long paymentAmount, LocalDateTime paidAt) {
        requireStatus(TradeStatus.CREATED, "pay");
        if (paymentAmount < 0) {
            throw new IllegalArgumentException("Material trade payment amount must not be negative");
        }
        this.paymentReference = paymentReference;
        this.paymentAmount = paymentAmount;
        this.paidAt = requireTimestamp(paidAt);
        this.status = TradeStatus.PAID;
    }

    /**
     * 판매자가 발송했다. 잠금은 그대로 둔다 — 배송 중 재판매를 막는 것이 이 잠금의 목적이다.
     */
    public void ship(LocalDateTime shippedAt) {
        requireStatus(TradeStatus.PAID, "ship");
        this.shippedAt = requireTimestamp(shippedAt);
        this.status = TradeStatus.SHIPPED;
    }

    /**
     * 구매자가 수령을 확인하고 Wallet 으로 서명했다. 2-Track 의 확정 지점이다.
     *
     * <p>보유량 이전과 잠금 소비는 여기서 하지 않는다. 다른 테이블이라 한 메서드로 묶을 수
     * 없고, 같은 트랜잭션 안에서 함께 처리하는 것이 호출부의 책임이다.
     */
    public void confirmReceipt(LocalDateTime receivedAt, LocalDateTime buyerSignedAt) {
        requireStatus(TradeStatus.SHIPPED, "confirm receipt of");
        this.receivedAt = requireTimestamp(receivedAt);
        this.buyerSignedAt = requireTimestamp(buyerSignedAt);
        this.status = TradeStatus.RECEIVED;
    }

    /** 보유량 이전까지 끝나 거래가 확정된다. */
    public void complete(LocalDateTime completedAt) {
        requireStatus(TradeStatus.RECEIVED, "complete");
        this.completedAt = requireTimestamp(completedAt);
        this.status = TradeStatus.COMPLETED;
    }

    /**
     * 거래가 취소된다. <b>발송 전까지만 가능하다.</b>
     *
     * <p>{@link TradeStatus#CREATED} 는 결제 전 이탈(결제창을 닫았거나 PG 가 실패)이고,
     * {@link TradeStatus#PAID} 는 결제 후 배송 전 취소다. 후자는 환불이 따라야 하지만
     * 그 처리는 PG 쪽이라 여기서는 상태만 남긴다.
     *
     * <p>{@link TradeStatus#SHIPPED} 부터는 막는다. 물건이 이미 나갔는데 거래만 없애면
     * 수량이 어디에도 속하지 않게 된다. 그 이후의 되돌리기는 분쟁 절차다.
     *
     * <p>판매 글의 잔여 수량 복구와 잠금 처리는 여기서 하지 않는다 — 다른 테이블이고,
     * 같은 트랜잭션에서 함께 처리하는 것이 호출부의 책임이다.
     */
    public void cancel() {
        if (this.status != TradeStatus.CREATED && this.status != TradeStatus.PAID) {
            throw new IllegalStateException(
                    "Material trade can only cancel while CREATED or PAID but was " + this.status);
        }
        this.status = TradeStatus.CANCELED;
    }

    /** 온체인 주문 번호를 연결한다. 한 번 정해지면 바뀌지 않는다. */
    public void assignChainTradeId(Long chainTradeId) {
        if (this.chainTradeId != null) {
            throw new IllegalStateException(
                    "Material trade chain id is already assigned: " + this.chainTradeId);
        }
        if (chainTradeId == null || chainTradeId <= 0) {
            throw new IllegalArgumentException("Material trade chain id must be positive");
        }
        this.chainTradeId = chainTradeId;
    }

    private void requireStatus(TradeStatus expected, String action) {
        if (this.status != expected) {
            throw new IllegalStateException(
                    "Material trade can only " + action + " while " + expected
                            + " but was " + this.status);
        }
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Material trade " + field + " must not be null");
        }
        return value;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Material trade " + field + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Material trade " + field + " must not exceed " + maxLength + " characters");
        }
        return value;
    }

    private static String requireWalletAddress(String walletAddress, String field) {
        if (walletAddress == null || walletAddress.isBlank()) {
            throw new IllegalArgumentException("Material trade " + field + " must not be blank");
        }
        if (!WALLET_ADDRESS_PATTERN.matcher(walletAddress).matches()) {
            throw new IllegalArgumentException(
                    "Material trade " + field + " must be a 0x-prefixed 40 digit hex string");
        }
        return walletAddress;
    }

    private static LocalDateTime requireTimestamp(LocalDateTime timestamp) {
        if (timestamp == null) {
            throw new IllegalArgumentException("Material trade timestamp must not be null");
        }
        return timestamp;
    }
}
