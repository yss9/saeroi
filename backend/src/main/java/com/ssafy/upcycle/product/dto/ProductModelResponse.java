package com.ssafy.upcycle.product.dto;

import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import java.time.LocalDateTime;

/**
 * 제품 모델 응답.
 *
 * <p>{@code stockQuantity} 는 저장된 값이 아니라 {@code product_item} 을 세어 구한 값이다.
 * 모델에 재고 컬럼을 두지 않는 것이 이 도메인의 규칙이라, 응답을 만들 때마다 센다.
 *
 * <p>등록 직후에는 언제나 {@code 0} 이다 — 개체는 생산 배치가 확정될 때 생긴다. 화면이
 * "등록은 됐지만 아직 팔 물건이 없다" 를 구분할 수 있게 그대로 내려보낸다.
 */
public record ProductModelResponse(
        Long productModelId,
        String name,
        ProductCategory category,
        Long brandId,
        long price,
        String description,
        String thumbnailUrl,
        ProductModelStatus status,
        long stockQuantity,
        LocalDateTime createdAt) {

    public static ProductModelResponse of(ProductModel model, long stockQuantity) {
        return new ProductModelResponse(
                model.getId(),
                model.getName(),
                model.getCategory(),
                model.getBrandId(),
                model.getPrice(),
                model.getDescription(),
                model.getThumbnailUrl(),
                model.getStatus(),
                stockQuantity,
                model.getCreatedAt());
    }
}
