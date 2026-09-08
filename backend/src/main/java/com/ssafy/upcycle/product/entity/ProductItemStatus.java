package com.ssafy.upcycle.product.entity;

/**
 * 제품 개체 하나의 상태. 재고의 실제 근거다 — 모델의 재고는 이 값으로 개체를 세어 구한다.
 *
 * <p>{@link #RESERVED} 가 있는 이유는 주문 생성과 결제 완료 사이에 시간이 있기 때문이다.
 * 그 사이 다른 사람이 같은 개체를 사가면, 결제를 마치고 돌아온 사용자에게 "없는 물건을
 * 샀다" 고 말해야 한다.
 */
public enum ProductItemStatus {

    IN_STOCK,
    RESERVED,
    SOLD
}
