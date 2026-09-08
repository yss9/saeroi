package com.ssafy.upcycle.product.entity;

/**
 * 제품 모델의 판매 상태.
 *
 * <p><b>{@link #SOLD_OUT} 은 재고가 0 이라는 사실의 사본이다.</b> product_model 에는 재고
 * 컬럼이 없고 재고는 {@code product_item} 을 세어 구한다. 그러므로 이 값은 판매자가 보는
 * 표시일 뿐 재고의 근거가 아니며, 실제 판매 가능 여부는 언제나 개체를 세어 판단한다.
 *
 * <p>{@link #DISCONTINUED} 는 재고와 무관하게 브랜드가 내린 상태다. 남은 개체가 있어도
 * 더 팔지 않는다.
 */
public enum ProductModelStatus {

    /** 등록 중. 아직 노출되지 않는다. */
    DRAFT,

    ON_SALE,
    SOLD_OUT,
    DISCONTINUED
}
