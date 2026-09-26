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
    private static final int CANCEL_REASON_MAX_LENGTH = 255;

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

    /**
     * 결제 마감.
     *
     * <p><b>수량은 결제가 아니라 구매 신청 시점에 깎인다</b>({@code listing.reduceAvailable}).
     * 결제하지 않고 이탈한 거래를 정리하지 않으면 그 수량이 영원히 잠긴다 — 남은 양을
     * 전부 사려던 거래였다면 판매 글이 그대로 품절로 보인다.
     *
     * <p>이 마감을 넘긴 거래는 {@code MaterialTradeExpiryWorker} 가 취소하며 판매 글
     * 수량을 되돌린다. {@code null} 인 행은 배치가 건드리지 않는다.
     */
    @Column(name = "payment_deadline")
    private LocalDateTime paymentDeadline;

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

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    /** 사람이 적은 취소 사유. 자동 정리처럼 사유가 없는 경로도 있어 null 을 허용한다. */
    @Column(name = "cancel_reason", length = CANCEL_REASON_MAX_LENGTH)
    private String cancelReason;

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
            PaymentMethod paymentMethod,
            LocalDateTime paymentDeadline) {
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
        this.paymentDeadline = requireNonNull(paymentDeadline, "payment deadline");
        this.status = TradeStatus.CREATED;
    }

    /**
     * 결제 마감을 넘겼는지.
     *
     * <p>넘긴 거래는 배치가 취소하며 판매 글 수량을 되돌린다. 상태를 함께 보는 이유는
     * 결제가 끝난 거래에 마감이 남아 있어도 정리 대상이 아니기 때문이다.
     */
    public boolean isPaymentExpiredAt(LocalDateTime at) {
        return this.status == TradeStatus.CREATED
                && paymentDeadline != null
                && !paymentDeadline.isAfter(requireNonNull(at, "at"));
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
            PaymentMethod paymentMethod,
            LocalDateTime paymentDeadline) {
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
                paymentMethod,
                paymentDeadline);
    }

    /**
     * 결제창으로 보내기 직전에 PG 거래 번호({@code tid})를 남긴다. <b>아직 청구되지 않았다.</b>
     *
     * <p>카카오페이는 준비 단계에서 tid 를 발급하고 승인·취소에 계속 그 값을 쓴다. 사용자가
     * 결제창에서 돌아왔을 때 서버가 그 tid 를 알고 있어야 승인을 요청할 수 있으므로 여기서
     * 저장한다. 사용자가 창을 닫으면 tid 만 남고 거래는 {@code CREATED} 그대로다.
     *
     * <p><b>그래서 {@code paymentReference} 만으로 "결제됨" 을 판단하면 안 된다.</b>
     * 준비 단계에서도 값이 채워지기 때문이다. 결제 여부는 {@code status}·{@code paidAt} 이
     * 답한다.
     *
     * <p>결제창을 다시 열면 새 tid 로 덮어쓴다 — 이전 tid 는 승인되지 않은 채 만료된다.
     */
    public void preparePayment(String paymentReference) {
        requireStatus(TradeStatus.CREATED, "prepare payment for");
        this.paymentReference = requireText(paymentReference, "payment reference", 100);
    }

    /** 결제가 승인되었다. PG 승인 tid 와 실제 승인 금액을 남긴다. */
    public void markPaid(
            String paymentReference,
            long paymentAmount,
            LocalDateTime paidAt,
            PaymentMethod settledWith) {
        requireStatus(TradeStatus.CREATED, "pay");
        if (paymentAmount < 0) {
            throw new IllegalArgumentException("Material trade payment amount must not be negative");
        }
        this.paymentReference = paymentReference;
        this.paymentAmount = paymentAmount;
        this.paidAt = requireTimestamp(paidAt);
        // 실제로 청구한 수단으로 덮어쓴다. 구매 요청이 보낸 값은 희망이고, 어느 게이트웨이가
        // 돌았는지는 서버만 안다 — 키가 없으면 Mock 이 붙는다. 요청 값을 그대로 남기면
        // 카카오페이로 청구된 거래가 MOCK 으로 기록되고, 대조나 환불 때 그 필드가 거짓이 된다.
        this.paymentMethod = requireNonNull(settledWith, "settled payment method");
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
     * 구매자가 물건을 받았다고 확인했다.
     *
     * <p><b>{@code buyer_signed_at} 은 여기서 채우지 않는다.</b> 예전에는 수령 시각과 같은
     * 값을 함께 넣었는데, 그러면 지갑 서명을 한 번도 하지 않은 거래가 "서명됨" 으로 남는다.
     * 그 값은 거래 검증의 유일한 근거이고({@code TradeVerificationService}), 판매 글의
     * {@code TRANSACTION_VERIFIED} 표시도 이것을 본다 — 거짓으로 채우면 그 아래 판정이
     * 모두 오염된다.
     *
     * <p>실제로 그렇게 새어 나갔다. 서버가 DB 의 이 값을 보고 "서명이 끝났다" 고 판단해
     * 수령 서명 카드를 아예 띄우지 않았고, 체인에서는 소재가 판매자 잠금에 그대로 남았다.
     * 나중에 그 소재를 가공에 보내려 할 때 컨트랙트가 insufficient material 로 거절해서야
     * 드러났다.
     *
     * <p>서명은 {@link #markBuyerSigned} 가 따로 받는다.
     *
     * <p>보유량 이전과 잠금 소비는 여기서 하지 않는다. 다른 테이블이라 한 메서드로 묶을 수
     * 없고, 같은 트랜잭션 안에서 함께 처리하는 것이 호출부의 책임이다.
     */
    public void confirmReceipt(LocalDateTime receivedAt) {
        requireStatus(TradeStatus.SHIPPED, "confirm receipt of");
        this.receivedAt = requireTimestamp(receivedAt);
        this.status = TradeStatus.RECEIVED;
    }

    /**
     * 구매자가 수령을 <b>체인에</b> 기록했다. 2-Track 의 확정 지점이다.
     *
     * <p>이 값이 채워진 거래만 검증된 거래다. 그래서 서명이 실제로 체인에 남았을 때만
     * 부른다 — 화면이 보낸 해시를 믿지 않고 체인을 읽어 확인한 뒤에.
     *
     * <p>이미 채워져 있으면 그대로 둔다. 같은 서명을 두 번 보고해도 첫 시각이 정본이다.
     */
    public void markBuyerSigned(LocalDateTime signedAt) {
        if (this.buyerSignedAt != null) {
            return;
        }
        this.buyerSignedAt = requireTimestamp(signedAt);
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
     *
     * <p><b>사유는 선택이다.</b> 결제창을 닫아 자동으로 정리되는 건처럼 사람이 이유를 적을
     * 자리가 없는 경로가 있다. 필수로 두면 그런 호출부가 의미 없는 문자열을 지어내게 된다.
     * 대신 시각은 언제나 남긴다 — 취소가 일어난 사실 자체는 예외가 없다.
     */
    public void cancel(String reason, LocalDateTime canceledAt) {
        if (this.status != TradeStatus.CREATED && this.status != TradeStatus.PAID) {
            throw new IllegalStateException(
                    "Material trade can only cancel while CREATED or PAID but was " + this.status);
        }
        this.status = TradeStatus.CANCELED;
        this.canceledAt = requireTimestamp(canceledAt);
        this.cancelReason = requireOptionalText(reason, "cancel reason", CANCEL_REASON_MAX_LENGTH);
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

    /** 빈 문자열은 없는 것과 같게 다룬다. 폼에서 입력하지 않은 칸은 null 이 아니라 "" 로 온다. */
    private static String requireOptionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireText(value, field, maxLength);
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
