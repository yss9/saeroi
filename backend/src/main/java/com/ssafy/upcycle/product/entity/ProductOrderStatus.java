package com.ssafy.upcycle.product.entity;

/**
 * 제품 주문의 상태.
 *
 * <pre>
 *   CREATED → PAID → SHIPPED → DELIVERED
 * </pre>
 *
 * <p><b>{@code CANCELED} 가 없다.</b> 소재 거래와 다른 점이다 — 제품 주문은 취소하면 상태를
 * 바꾸는 대신 {@code canceled_product_order} 로 옮기고 원본 행을 지운다.
 * {@code product_order_item.product_item_id} 의 UNIQUE 를 유지하면서 취소된 개체를 다시
 * 팔려면 원본이 사라져야 하기 때문이다.
 *
 * <p>{@link #DELIVERED} 는 브랜드의 배송 처리만으로 도달하지 않는다. 소비자가 수령을
 * 확인해야 한다 — 소재 거래와 같은 2-Track 이다. 다만 소비자는 지갑을 쓰지 않으므로
 * 서명도 온체인 기록도 없다.
 */
public enum ProductOrderStatus {

    CREATED,
    PAID,
    SHIPPED,
    DELIVERED
}
