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
 * 취소된 주문에 담겨 있던 개체.
 *
 * <p><b>{@code product_item_id} 에 UNIQUE 가 없다.</b> 원본 테이블과 다른 점이고 의도된
 * 것이다 — 취소된 개체는 다시 팔리고, 또 취소될 수 있다. 같은 개체가 이 테이블에 여러 번
 * 들어오는 것이 정상이다.
 *
 * <p>{@code original_order_item_id} 도 FK 가 아니다. 원본 행이 이미 삭제됐다.
 */
@Getter
@Entity
@Table(name = "canceled_product_order_item")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CanceledProductOrderItem {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "canceled_product_order_id", nullable = false)
    private Long canceledProductOrderId;

    /** 삭제된 원본 주문 항목의 PK. FK 가 아니다. */
    @Column(name = "original_order_item_id", nullable = false)
    private Long originalOrderItemId;

    @Column(name = "product_item_id", nullable = false)
    private Long productItemId;

    @Column(name = "product_model_id", nullable = false)
    private Long productModelId;

    /** 주문 당시 단가 (원). */
    @Column(name = "price", nullable = false)
    private long price;

    private CanceledProductOrderItem(Long canceledProductOrderId, ProductOrderItem item) {
        if (canceledProductOrderId == null) {
            throw new IllegalArgumentException(
                    "Canceled product order item order id must not be null");
        }
        if (item == null || item.getId() == null) {
            throw new IllegalArgumentException(
                    "Canceled product order item source must be persisted");
        }
        this.canceledProductOrderId = canceledProductOrderId;
        this.originalOrderItemId = item.getId();
        this.productItemId = item.getProductItemId();
        this.productModelId = item.getProductModelId();
        this.price = item.getPrice();
    }

    /** 지워질 주문 항목을 이력으로 옮긴다. */
    public static CanceledProductOrderItem from(
            Long canceledProductOrderId, ProductOrderItem item) {
        return new CanceledProductOrderItem(canceledProductOrderId, item);
    }
}
