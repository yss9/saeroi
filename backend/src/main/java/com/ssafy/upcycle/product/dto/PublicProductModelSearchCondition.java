package com.ssafy.upcycle.product.dto;

import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductModelStatus;

/**
 * 비회원도 사용할 수 있는 상품 목록 조건.
 *
 * <p>{@code status} 를 받지 않는 것이 핵심이다. 요청 파라미터로 {@code DRAFT} 를 보내도
 * 공개 목록은 언제나 {@link ProductModelStatus#ON_SALE} 만 조회한다.
 */
public record PublicProductModelSearchCondition(
        ProductCategory category,
        Long brandId,
        Integer page,
        Integer size) {

    public ProductModelSearchCondition toOnSaleCondition() {
        return new ProductModelSearchCondition(
                ProductModelStatus.ON_SALE, category, brandId, page, size);
    }
}
