package com.ssafy.upcycle.product.dto;

import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import java.time.LocalDateTime;

/** 소비자 쇼핑몰에 공개하는 판매 상품 정보. */
public record PublicProductModelResponse(
        Long productModelId,
        String name,
        ProductCategory category,
        Long brandId,
        String brandName,
        long price,
        String description,
        String thumbnailUrl,
        ProductModelStatus status,
        long stockQuantity,
        LocalDateTime createdAt) {

    private static final String PUBLIC_THUMBNAIL_PATH =
            "/api/v1/public/product-models/%d/thumbnail";

    public static PublicProductModelResponse of(
            ProductModel model, String brandName, long stockQuantity) {
        return new PublicProductModelResponse(
                model.getId(),
                model.getName(),
                model.getCategory(),
                model.getBrandId(),
                brandName,
                model.getPrice(),
                model.getDescription(),
                PUBLIC_THUMBNAIL_PATH.formatted(model.getId()),
                model.getStatus(),
                stockQuantity,
                model.getCreatedAt());
    }
}
