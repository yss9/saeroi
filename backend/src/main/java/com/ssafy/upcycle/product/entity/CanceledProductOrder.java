package com.ssafy.upcycle.product.entity;

import com.ssafy.upcycle.trade.entity.PaymentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * 취소된 주문의 이력.
 *
 * <p><b>왜 상태를 바꾸지 않고 옮기는가.</b> {@code product_order_item.product_item_id} 에
 * UNIQUE 가 걸려 있어, 취소된 개체를 다시 팔려면 그 행이 사라져야 한다. 주문을
 * {@code CANCELED} 상태로 남겨 두면 개체가 영영 그 주문에 묶인다. 그래서 취소는
 * 원본을 지우고 이 테이블로 옮긴다 — 소재 거래가 상태만 바꾸는 것과 다른 점이다.
 *
 * <p><b>{@code original_order_id} 에 FK 를 걸지 않는다.</b> 가리키는 원본이 이미 삭제되기
 * 때문이다. 값은 남기되 참조 무결성은 DB 가 보장하지 않는다.
 *
 * <p>{@code order_code} 에도 UNIQUE 를 걸지 않는다. 원본 테이블에서 코드가 풀렸으므로 같은
 * 코드가 다시 발급될 수 있고, 그 주문이 또 취소되면 여기 같은 값이 두 번 들어온다.
 *
 * <p>감사 컬럼 대신 {@code original_created_at} 과 {@code canceled_at} 을 직접 갖는다.
 * 이 행이 만들어진 시각은 취소 시각과 같으므로 별도 {@code created_at} 은 중복이고,
 * 원본이 언제 주문됐는지는 지워지기 전에 복사해 두어야 한다.
 */
@Getter
@Entity
@Table(name = "canceled_product_order")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CanceledProductOrder {

    private static final int CANCEL_REASON_MAX_LENGTH = 255;

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 삭제된 원본 주문의 PK. FK 가 아니다 — 가리킬 행이 이미 없다. */
    @Column(name = "original_order_id", nullable = false, unique = true)
    private Long originalOrderId;

    @Column(name = "order_code", nullable = false, length = 30)
    private String orderCode;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    /** 주문 당시 총액 (원). */
    @Column(name = "total_price", nullable = false)
    private long totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    /** 사람이 적은 취소 사유. 자동 만료처럼 사유가 없는 경로도 있어 null 을 허용한다. */
    @Column(name = "cancel_reason", length = CANCEL_REASON_MAX_LENGTH)
    private String cancelReason;

    @Column(name = "original_created_at", nullable = false)
    private LocalDateTime originalCreatedAt;

    @Column(name = "original_paid_at")
    private LocalDateTime originalPaidAt;

    @Column(name = "canceled_at", nullable = false)
    private LocalDateTime canceledAt;

    private CanceledProductOrder(
            ProductOrder order, String cancelReason, LocalDateTime canceledAt) {
        if (order == null) {
            throw new IllegalArgumentException("Canceled product order source must not be null");
        }
        if (order.getId() == null) {
            throw new IllegalArgumentException("Canceled product order source must be persisted");
        }
        this.originalOrderId = order.getId();
        this.orderCode = order.getOrderCode();
        this.buyerId = order.getBuyerId();
        this.brandId = order.getBrandId();
        this.totalPrice = order.getTotalPrice();
        this.paymentMethod = order.getPaymentMethod();
        this.paymentReference = order.getPaymentReference();
        this.cancelReason = requireOptionalText(cancelReason, CANCEL_REASON_MAX_LENGTH);
        this.originalCreatedAt = requireTimestamp(order.getCreatedAt(), "original created at");
        this.originalPaidAt = order.getPaidAt();
        this.canceledAt = requireTimestamp(canceledAt, "canceled at");
    }

    /**
     * 지워질 주문을 이력으로 옮긴다.
     *
     * <p>원본을 통째로 받는 이유는 옮길 값이 여덟 개라서다. 위치 인자로 늘어놓으면 호출부가
     * 순서를 틀렸을 때 컴파일이 통과한다 — {@code buyerId} 와 {@code brandId} 처럼 같은
     * 타입이 이웃해 있으면 특히 그렇다.
     */
    public static CanceledProductOrder from(
            ProductOrder order, String cancelReason, LocalDateTime canceledAt) {
        return new CanceledProductOrder(order, cancelReason, canceledAt);
    }

    private static String requireOptionalText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Canceled product order cancel reason must not exceed "
                            + maxLength + " characters");
        }
        return value;
    }

    private static LocalDateTime requireTimestamp(LocalDateTime timestamp, String field) {
        if (timestamp == null) {
            throw new IllegalArgumentException(
                    "Canceled product order " + field + " must not be null");
        }
        return timestamp;
    }
}
