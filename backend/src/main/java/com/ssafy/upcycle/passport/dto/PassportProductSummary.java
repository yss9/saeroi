package com.ssafy.upcycle.passport.dto;

import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductModel;

/**
 * Passport 가 공개하는 제품 정보. {@code ProductModelResponse} 와 의도적으로 다르다 —
 * {@code brandId}·{@code price} 가 없다. 브랜드 식별자와 판매가는 §6-3 결정이 막은
 * "거래 정보" 다.
 */
public record PassportProductSummary(
        String name,
        ProductCategory category,
        String description,
        String thumbnailUrl) {

    public static PassportProductSummary of(ProductModel model) {
        return new PassportProductSummary(
                model.getName(), model.getCategory(), model.getDescription(), model.getThumbnailUrl());
    }
}
