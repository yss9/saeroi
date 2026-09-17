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
 * 한 번의 결제로 함께 만들어진 주문들의 묶음.
 *
 * <h2>왜 주문 밖에 결제가 있는가</h2>
 *
 * <p>장바구니에 브랜드가 둘 이상 담기면 <b>주문은 브랜드 수만큼</b> 생긴다. 브랜드가 자기
 * 주문만 관리하는 기존 구조를 그대로 두기 위해서다 — 배송도 취소도 정산도 브랜드 단위다.
 *
 * <p>그런데 <b>결제는 한 번</b>이어야 한다. 브랜드마다 결제창이 뜨면 소비자는 같은 장바구니를
 * 사면서 카드를 세 번 꺼내게 된다. 결제의 주체가 주문일 수 없는 이유가 이것이고, 그래서
 * {@code tid}·금액·마감이 여기에 있다.
 *
 * <h2>단일 주문은 여기를 쓰지 않는다</h2>
 *
 * <p>상품 상세의 "바로 구매" 는 {@code ProductOrder} 의 결제 컬럼을 그대로 쓴다
 * ({@code order_group_id} 가 {@code null}). 결제·환불·QR 이 이미 붙어 있는 경로라 건드리면
 * 위험이 크고, 이 묶음은 <b>기존 경로를 그대로 둔 채</b> 새 입구를 여는 방식이다.
 *
 * <p>그래서 결제 컬럼이 두 곳에 있다. 그 대신 게이트웨이를 부르고 금액을 대조하는 일은
 * {@code ProductPaymentCoordinator} 하나가 맡아 두 경로가 같은 검증을 지난다 — 나뉘는 것은
 * "무엇이 결제의 단위인가" 뿐이다.
 *
 * <p><b>결제 상태 전이는 아직 없다.</b> 준비·승인·취소는 다음 작업에서 이 묶음 단위로
 * 붙인다. 지금은 주문을 묶어 만드는 것까지다.
 */
@Getter
@Entity
@Table(name = "product_order_group")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ProductOrderGroup {

    public static final int GROUP_CODE_MAX_LENGTH = 30;

    /** 확정 전 임시 코드의 접두어. 주문과 같은 방식이다 — id 를 받아야 코드를 정할 수 있다. */
    public static final String TEMPORARY_CODE_PREFIX = "TMP-";

    private static final int PAYMENT_REFERENCE_MAX_LENGTH = 100;

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 고객이 문의할 때 대는 묶음 번호. 주문번호와 자리를 나눠 쓰지 않는다. */
    @Column(name = "group_code", nullable = false, length = GROUP_CODE_MAX_LENGTH, unique = true)
    private String groupCode;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    /**
     * 묶음에 속한 주문들의 금액 합. <b>결제창에 청구하는 금액이 이 값이다.</b>
     *
     * <p>주문이 저마다 {@code total_price} 를 갖는 것과 별개다. 브랜드별 주문 금액은 그
     * 브랜드의 정산 금액이고, 이 값은 소비자가 한 번에 내는 금액이다.
     */
    @Column(name = "total_price", nullable = false)
    private long totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProductOrderGroupStatus status;

    /** 희망 수단일 뿐이다. 실제로 청구한 게이트웨이는 승인 시점에 다시 기록한다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    /** 카카오페이 {@code tid}. 결제창에서 돌아온 사용자를 승인시키려면 서버가 알고 있어야 한다. */
    @Column(name = "payment_reference", length = PAYMENT_REFERENCE_MAX_LENGTH)
    private String paymentReference;

    @Column(name = "payment_amount", nullable = false)
    private long paymentAmount;

    /** 결제창을 열어 둘 수 있는 시한. 넘긴 묶음은 정리되고 개체는 재고로 돌아간다. */
    @Column(name = "payment_deadline")
    private LocalDateTime paymentDeadline;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    private ProductOrderGroup(
            String groupCode,
            Long buyerId,
            long totalPrice,
            PaymentMethod paymentMethod,
            LocalDateTime paymentDeadline) {
        this.groupCode = requireText(groupCode, "group code", GROUP_CODE_MAX_LENGTH);
        this.buyerId = requireNonNull(buyerId, "buyer id");
        if (totalPrice < 0) {
            throw new IllegalArgumentException(
                    "Product order group total price must not be negative");
        }
        this.totalPrice = totalPrice;
        this.paymentMethod = requireNonNull(paymentMethod, "payment method");
        this.paymentDeadline = paymentDeadline;
        this.status = ProductOrderGroupStatus.CREATED;
        this.paymentAmount = 0L;
    }

    /**
     * 묶음을 연다. <b>결제 전 상태다.</b>
     *
     * <p>속한 주문들도 함께 {@code CREATED} 이고 개체는 {@code RESERVED} 로 잡혀 있다.
     * 돈이 오가는 것은 승인 시점이다 — 단일 주문과 같은 2단계다.
     */
    public static ProductOrderGroup open(
            String groupCode,
            Long buyerId,
            long totalPrice,
            PaymentMethod paymentMethod,
            LocalDateTime paymentDeadline) {
        return new ProductOrderGroup(
                groupCode, buyerId, totalPrice, paymentMethod, paymentDeadline);
    }

    /**
     * 임시 코드를 확정된 묶음 번호로 교체한다.
     *
     * <p><b>이미 정식 코드가 붙었으면 거부한다.</b> 고객이 적어 둔 번호가 나중에 바뀌면 그
     * 번호로는 아무것도 찾지 못한다 — 주문번호와 같은 이유다.
     */
    public void assignCode(String groupCode) {
        if (!this.groupCode.startsWith(TEMPORARY_CODE_PREFIX)) {
            throw new IllegalStateException(
                    "Product order group code is already assigned: " + this.groupCode);
        }
        this.groupCode = requireText(groupCode, "group code", GROUP_CODE_MAX_LENGTH);
    }

    public boolean isOwnedBy(Long buyerId) {
        return this.buyerId.equals(buyerId);
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Product order group " + field + " must not be null");
        }
        return value;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Product order group " + field + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Product order group " + field + " must not exceed "
                            + maxLength + " characters");
        }
        return value;
    }
}
