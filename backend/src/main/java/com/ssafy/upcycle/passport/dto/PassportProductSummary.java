package com.ssafy.upcycle.passport.dto;

import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductModel;

/**
 * Passport 가 공개하는 제품 정보. {@code ProductModelResponse} 와 의도적으로 다르다 —
 * {@code brandId}·{@code price} 가 없다. 내부 식별자와 판매가는 §6-3 결정이 막은
 * "거래 정보" 다. 화면에 필요한 공개 상호명만 {@code brandName}으로 제공한다.
 *
 * <p><b>{@code thumbnailUrl}(저장 키)도 없다.</b> 키 자체가 {@code FileReference(type, id)}
 * 의 {@code id} 를 경로에 담고 있어(브랜드의 내부 id), 문자열로 내려주면 값을 빼기로 한
 * {@code brandId} 가 다른 필드 안에 그대로 새는 셈이다. 화면은 이 필드를 조립하지 않고
 * {@code GET /api/v1/qr/{qrToken}/thumbnail} 을 곧장 부른다({@code PassportController}).
 */
public record PassportProductSummary(
        String name,
        String brandName,
        ProductCategory category,
        String description,
        String imageUrl) {

    public static PassportProductSummary of(ProductModel model, String brandName, String imageUrl) {
        return new PassportProductSummary(
                model.getName(), brandName, model.getCategory(), model.getDescription(), imageUrl);
    }
}
