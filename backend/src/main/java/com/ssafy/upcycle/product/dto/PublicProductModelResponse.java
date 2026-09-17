package com.ssafy.upcycle.product.dto;

import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductImage;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import com.ssafy.upcycle.product.entity.ProductSubcategory;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 소비자 쇼핑몰에 공개하는 판매 상품 정보.
 *
 * <p>사진이 둘로 나뉜다. {@code thumbnailUrl} 은 <b>언제나 있는 한 장</b>이고 목록 카드가
 * 쓴다. {@code images} 는 상세 화면이 넘겨 보는 갤러리이고 <b>비어 있을 수 있다</b> —
 * 브랜드가 아직 여러 장을 걸지 않은 제품이 그렇다. 화면은 갤러리가 비면 대표 사진 한
 * 장으로 물러서면 된다.
 *
 * <p>갤러리를 목록 응답에서도 채우는 것은 같은 응답 모양을 두 곳에서 쓰기 때문이다.
 * 상세에만 채우면 목록의 {@code images} 가 "없다" 인지 "안 실었다" 인지 구분되지 않는다.
 * 질의는 페이지당 한 번이라 제품 수와 무관하다.
 */
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
        List<ProductImageResponse> images,
        LocalDateTime createdAt) {

    private static final String PUBLIC_THUMBNAIL_PATH =
            "/api/v1/public/product-models/%d/thumbnail";

    public static PublicProductModelResponse of(
            ProductModel model,
            String brandName,
            long stockQuantity,
            List<ProductImage> images) {
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
                images.stream().map(ProductImageResponse::publicOf).toList(),
                model.getCreatedAt());
    }

    private static String publicThumbnailUrl(ProductModel model) {
        String thumbnailUrl = model.getThumbnailUrl();
        // 데모 상품은 프론트에 이미 포함된 공개 정적 이미지를 쓴다. 브랜드가 업로드한
        // 이미지는 저장 키라 이 접두어를 가질 수 없고 기존 공개 썸네일 API를 그대로 쓴다.
        //
        // 이 분기는 데모 상품 이미지가 FileStorageService 로 옮겨지면 사라진다. 지금
        // 지우면 이미 시드된 상품들의 대표 이미지가 통째로 깨진다 — 그 상품들의
        // thumbnail_url 은 저장 키가 아니라 프론트 정적 경로다.
        if (thumbnailUrl.startsWith("/images/landing/")) {
            return thumbnailUrl;
        }
        return PUBLIC_THUMBNAIL_PATH.formatted(model.getId());
    }
}
