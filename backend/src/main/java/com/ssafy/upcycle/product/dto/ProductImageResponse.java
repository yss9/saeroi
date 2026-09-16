package com.ssafy.upcycle.product.dto;

import com.ssafy.upcycle.product.entity.ProductImage;
import com.ssafy.upcycle.product.entity.ProductImageType;

/**
 * 갤러리 사진 한 장.
 *
 * <p><b>저장 키를 내보내지 않는다.</b> 키는 저장소 안의 경로라 화면이 쓸 수 없고, 내보내면
 * 프론트가 그것으로 주소를 조립하려 든다 — 그 순간 저장 방식이 화면에 새고, 로컬에서 되던
 * 것이 S3 배포에서 깨진다.
 *
 * <p>대신 <b>바이트를 흘려 주는 우리 서버 주소</b>를 준다. 형식은 한 가지다.
 *
 * <pre>
 *   소비자  : /api/v1/public/product-models/{productModelId}/images/{productImageId}
 *   브랜드  : /api/v1/product-models/{productModelId}/images/{productImageId}
 * </pre>
 *
 * <p>둘로 나뉜 것은 보는 사람이 다르기 때문이지 제품이 달라서가 아니다 — 시연용 상품과
 * 브랜드가 올린 상품이 같은 주소 형식을 쓴다. 앞의 것은 판매 중인 상품만 열리고 공용
 * 캐시를 허용하며, 뒤의 것은 토큰을 요구하고 캐시하지 않는다.
 */
public record ProductImageResponse(
        Long productImageId,
        String url,
        ProductImageType type,
        int sortOrder) {

    private static final String PUBLIC_PATH = "/api/v1/public/product-models/%d/images/%d";
    private static final String MANAGEMENT_PATH = "/api/v1/product-models/%d/images/%d";

    /** 소비자에게 내보낼 모양. */
    public static ProductImageResponse publicOf(ProductImage image) {
        return of(image, PUBLIC_PATH);
    }

    /** 브랜드 관리 화면에 내보낼 모양. */
    public static ProductImageResponse managementOf(ProductImage image) {
        return of(image, MANAGEMENT_PATH);
    }

    private static ProductImageResponse of(ProductImage image, String pathFormat) {
        return new ProductImageResponse(
                image.getId(),
                pathFormat.formatted(image.getProductModelId(), image.getId()),
                image.getImageType(),
                image.getSortOrder());
    }
}
