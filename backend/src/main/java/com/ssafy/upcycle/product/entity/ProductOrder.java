package com.ssafy.upcycle.product.entity;

import com.ssafy.upcycle.trade.entity.PaymentMethod;
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
 * 소비자의 제품 주문.
 *
 * <p><b>소재 거래와 달리 소유권 이전이 없다.</b> 제품을 팔 때 소재를 다시 차감하지 않는다 —
 * 소재는 생산 확정 시점에 이미 소비됐다. 그래서 이 주문에는 잠금도, 지갑 서명도, 온체인
 * 기록도 없다. 소비자는 지갑을 쓰지 않는다.
 *
 * <p><b>취소하면 이 행이 사라진다.</b> 상태에 {@code CANCELED} 가 없는 이유다. 취소된
 * 주문은 {@code canceled_product_order} 로 옮겨지고, 그래야
 * {@code product_order_item.product_item_id} 의 UNIQUE 를 유지하면서 개체를 다시 팔 수 있다.
 *
 * <p>{@code payment_deadline} 은 생성 시각 + 30분이다. 넘긴 주문은 배치가 정리하며, 그때
 * 잡아 둔 개체가 재고로 돌아온다. 이 값이 없으면 결제창을 닫은 주문마다 개체가 영구히
 * {@code RESERVED} 로 묶인다.
 */
@Getter
@Entity
@Table(name = "product_order")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ProductOrder {

    /**
     * {@code order_code} 컬럼 길이. {@code ProductOrderCodeGenerator} 가 참조한다 —
     * 두 곳에 숫자를 두면 한쪽만 바뀔 때 조용히 길이가 어긋난다.
     */
    public static final int ORDER_CODE_MAX_LENGTH = 30;

    /**
     * INSERT 를 통과시키기 위한 임시 코드의 접두어.
     *
     * <p>{@code order_code} 가 NOT NULL UNIQUE 라 INSERT 시점에 값이 있어야 하는데,
     * 사람이 읽는 주문번호는 AUTO_INCREMENT 로 확정된 id 에서 만든다.
     * {@code MaterialCodeGenerator} 와 같은 2단계다.
     */
    public static final String TEMPORARY_CODE_PREFIX = "TMP-";
    private static final int PAYMENT_REFERENCE_MAX_LENGTH = 100;

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_code", nullable = false, length = ORDER_CODE_MAX_LENGTH, unique = true)
    private String orderCode;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    /** 파는 브랜드. 한 주문은 한 브랜드의 제품만 담는다. */
    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    /** 주문 시점 총액 (원). 모델 가격이 나중에 바뀌어도 이 값은 그대로다. */
    @Column(name = "total_price", nullable = false)
    private long totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProductOrderStatus status;

    /**
     * 소재 거래의 {@code PaymentMethod} 를 그대로 쓴다. 두 테이블의 ENUM 값이 같고, 결제
     * 자체가 도메인마다 다른 개념이 아니기 때문이다. 복사해 두면 두 열거형을 손으로 맞춰야 한다.
     *
     * <p>다만 이 타입이 trade 패키지에 있는 것은 자리가 맞지 않는다. 결제 게이트웨이를 제품
     * 주문도 쓰게 될 때 {@code common} 으로 옮기는 편이 낫다 — 지금 옮기면 trade 파일 여러
     * 개를 이 티켓에서 건드리게 된다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    /** PG 승인 tid. 결제 전에는 비어 있다. */
    @Column(name = "payment_reference", length = PAYMENT_REFERENCE_MAX_LENGTH)
    private String paymentReference;

    /** 실제 승인된 금액 (원). */
    @Column(name = "payment_amount", nullable = false)
    private long paymentAmount;

    /** 생성 + 30분. 넘기면 배치가 정리하고 개체가 재고로 돌아온다. */
    @Column(name = "payment_deadline")
    private LocalDateTime paymentDeadline;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    private ProductOrder(
            String orderCode,
            Long buyerId,
            Long brandId,
            long totalPrice,
            PaymentMethod paymentMethod,
            LocalDateTime paymentDeadline) {
        this.orderCode = requireText(orderCode, "order code", ORDER_CODE_MAX_LENGTH);
        this.buyerId = requireNonNull(buyerId, "buyer id");
        this.brandId = requireNonNull(brandId, "brand id");
        if (buyerId.equals(brandId)) {
            throw new IllegalArgumentException("Product order buyer and brand must differ");
        }
        if (totalPrice < 0) {
            throw new IllegalArgumentException("Product order total price must not be negative");
        }
        this.totalPrice = totalPrice;
        this.paymentMethod = requireNonNull(paymentMethod, "payment method");
        this.paymentDeadline = paymentDeadline;
        this.paymentAmount = 0L;
        this.status = ProductOrderStatus.CREATED;
    }

    /** 주문이 만들어진다. 결제 전이므로 CREATED 로 시작한다. */
    public static ProductOrder place(
            String orderCode,
            Long buyerId,
            Long brandId,
            long totalPrice,
            PaymentMethod paymentMethod,
            LocalDateTime paymentDeadline) {
        return new ProductOrder(
                orderCode, buyerId, brandId, totalPrice, paymentMethod, paymentDeadline);
    }

    /**
     * 임시 코드를 확정된 주문번호로 교체한다.
     *
     * <p><b>이미 정식 코드가 붙은 주문은 거부한다.</b> 주문번호는 고객이 문의할 때
     * 대는 식별자라, 나중에 바뀌면 고객이 적어 둔 번호로는 아무것도 찾지 못한다.
     */
    public void assignCode(String orderCode) {
        if (!this.orderCode.startsWith(TEMPORARY_CODE_PREFIX)) {
            throw new IllegalStateException(
                    "Product order code is already assigned: " + this.orderCode);
        }
        this.orderCode = requireText(orderCode, "order code", ORDER_CODE_MAX_LENGTH);
    }

    /** 결제가 승인되었다. */
    public void markPaid(String paymentReference, long paymentAmount, LocalDateTime paidAt) {
        requireStatus(ProductOrderStatus.CREATED, "pay");
        if (paymentAmount < 0) {
            throw new IllegalArgumentException("Product order payment amount must not be negative");
        }
        this.paymentReference = paymentReference;
        this.paymentAmount = paymentAmount;
        this.paidAt = requireTimestamp(paidAt);
        this.status = ProductOrderStatus.PAID;
    }

    /** 브랜드가 발송했다. 이것만으로 주문이 완료되지는 않는다. */
    public void ship(LocalDateTime shippedAt) {
        requireStatus(ProductOrderStatus.PAID, "ship");
        this.shippedAt = requireTimestamp(shippedAt);
        this.status = ProductOrderStatus.SHIPPED;
    }

    /**
     * 소비자가 수령을 확인했다. 2-Track 의 확정 지점이다.
     *
     * <p>소재 거래와 달리 서명이 없다. 소비자는 지갑을 쓰지 않으므로 확인 행위 자체가
     * 유일한 근거다.
     */
    public void confirmDelivery(LocalDateTime deliveredAt) {
        requireStatus(ProductOrderStatus.SHIPPED, "confirm delivery of");
        this.deliveredAt = requireTimestamp(deliveredAt);
        this.status = ProductOrderStatus.DELIVERED;
    }

    /**
     * 취소할 수 있는 상태인지.
     *
     * <p>발송 이후는 막는다. 물건이 나갔는데 주문만 없애면 그 개체가 어디에도 속하지 않게
     * 된다. 취소 자체는 이 엔티티가 하지 않는다 — 행을 지우고 이력 테이블로 옮기는
     * 작업이라 여러 테이블에 걸치고, 호출부가 한 트랜잭션으로 처리한다.
     */
    public boolean isCancelable() {
        return this.status == ProductOrderStatus.CREATED || this.status == ProductOrderStatus.PAID;
    }

    /** 결제 마감을 넘겼는지. 넘긴 주문은 배치가 정리하고 개체를 재고로 되돌린다. */
    public boolean isPaymentExpiredAt(LocalDateTime at) {
        return this.status == ProductOrderStatus.CREATED
                && paymentDeadline != null
                && !paymentDeadline.isAfter(requireTimestamp(at));
    }

    private void requireStatus(ProductOrderStatus expected, String action) {
        if (this.status != expected) {
            throw new IllegalStateException(
                    "Product order can only " + action + " while " + expected
                            + " but was " + this.status);
        }
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Product order " + field + " must not be null");
        }
        return value;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Product order " + field + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Product order " + field + " must not exceed " + maxLength + " characters");
        }
        return value;
    }

    private static LocalDateTime requireTimestamp(LocalDateTime timestamp) {
        if (timestamp == null) {
            throw new IllegalArgumentException("Product order timestamp must not be null");
        }
        return timestamp;
    }
}
