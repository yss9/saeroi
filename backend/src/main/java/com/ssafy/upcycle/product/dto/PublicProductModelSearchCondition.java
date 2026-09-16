package com.ssafy.upcycle.product.dto;

import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import com.ssafy.upcycle.product.entity.ProductSubcategory;

/**
 * 비회원도 사용할 수 있는 상품 목록 조건.
 *
 * <p>{@code status} 를 받지 않는 것이 핵심이다. 요청 파라미터로 {@code DRAFT} 를 보내도
 * 공개 목록은 언제나 {@link ProductModelStatus#ON_SALE} 만 조회한다.
 *
 * <p>정렬은 소비자 SHOP 이 쓰는 셋뿐이다 — 최신순(기본)·가격 낮은순·가격 높은순
 * ({@link ProductModelSort}).
 *
 * <p><b>{@code category=ETC} 를 거절하지 않는다.</b> 거절하려면 목록 조회가 400 을 내야
 * 하는데, 그 분류는 소비자에게 존재하지 않는 것으로 다루는 편이 일관된다 — 공개 목록은
 * 조건과 무관하게 ETC 를 제외하므로 결과가 빈 목록이 된다.
 */
public record PublicProductModelSearchCondition(
        ProductCategory category,
        ProductSubcategory subcategory,
        Long brandId,
        ProductModelSort sort,
        Integer page,
        Integer size) {

    public ProductModelSearchCondition toOnSaleCondition() {
        return new ProductModelSearchCondition(
                ProductModelStatus.ON_SALE, category, subcategory, brandId, sort, page, size);
    }
}
