package com.ssafy.upcycle.product.dto;

import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import com.ssafy.upcycle.product.entity.ProductSubcategory;
import java.time.LocalDateTime;

/** 소비자 쇼핑몰에 공개하는 판매 상품 정보. */
public record PublicProductModelResponse(
        Long productModelId,
        String name,
        ProductCategory category,
        ProductSubcategory subcategory,
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
                model.getSubcategory(),
                model.getBrandId(),
                brandName,
                model.getPrice(),
                model.getDescription(),
                publicThumbnailUrl(model),
                model.getStatus(),
                stockQuantity,
                model.getCreatedAt());
    }

    private static String publicThumbnailUrl(ProductModel model) {
        String thumbnailUrl = model.getThumbnailUrl();
        // 시연 상품은 프론트에 이미 포함된 공개 정적 이미지를 쓴다. 브랜드가 업로드한
        // 이미지는 저장 키라 이 접두어를 가질 수 없고 기존 공개 썸네일 API를 그대로 쓴다.
        if (thumbnailUrl.startsWith("/images/landing/")) {
            return thumbnailUrl;
        }
        return PUBLIC_THUMBNAIL_PATH.formatted(model.getId());
    }
}
