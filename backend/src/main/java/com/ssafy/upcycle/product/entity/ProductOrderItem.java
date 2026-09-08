package com.ssafy.upcycle.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문에 담긴 제품 개체 한 점.
 *
 * <p><b>{@code product_item_id} 의 UNIQUE 가 이 테이블의 핵심이다.</b> 같은 개체가 두 주문에
 * 들어가는 것을 DB 가 막는다. 동시 구매를 막는 로직({@link ProductItem#reserve})에 구멍이
 * 있어도 이 제약이 최후 방어선이 된다.
 *
 * <p>그래서 취소는 상태를 바꾸는 대신 행을 지운다. 제약을 유지한 채 취소된 개체를 다시
 * 팔려면 이 행이 사라져야 하고, 사라진 기록은 {@code canceled_product_order_item} 이 받는다.
 *
 * <p>{@code price} 는 주문 시점 단가 스냅샷이다. {@code product_model.price} 를 나중에 읽으면
 * 가격이 바뀐 뒤의 값이 나와 이미 팔린 주문의 금액이 소급해 변한다.
 *
 * <p>감사 컬럼이 없다. 주문 시각은 {@code product_order} 가 갖고, 이 행은 그 주문에 속한
 * 구성 요소일 뿐이다.
 */
@Getter
@Entity
@Table(name = "product_order_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductOrderItem {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_order_id", nullable = false)
    private Long productOrderId;

    /** 팔린 개체. UNIQUE 라 한 개체는 한 주문에만 들어간다. */
    @Column(name = "product_item_id", nullable = false, unique = true)
    private Long productItemId;

    /** 조회 편의. 개체를 거치지 않고 어떤 모델이 팔렸는지 알 수 있다. */
    @Column(name = "product_model_id", nullable = false)
    private Long productModelId;

    /** 주문 시점 단가 (원). 모델 가격이 바뀌어도 이 값은 그대로다. */
    @Column(name = "price", nullable = false)
    private long price;

    private ProductOrderItem(
            Long productOrderId, Long productItemId, Long productModelId, long price) {
        this.productOrderId = requireNonNull(productOrderId, "product order id");
        this.productItemId = requireNonNull(productItemId, "product item id");
        this.productModelId = requireNonNull(productModelId, "product model id");
        if (price < 0) {
            throw new IllegalArgumentException("Product order item price must not be negative");
        }
        this.price = price;
    }

    public static ProductOrderItem of(
            Long productOrderId, Long productItemId, Long productModelId, long price) {
        return new ProductOrderItem(productOrderId, productItemId, productModelId, price);
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Product order item " + field + " must not be null");
        }
        return value;
    }
}
