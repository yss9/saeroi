package com.ssafy.upcycle.passport.dto;

import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductItemStatus;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.traceability.dto.ProductJourneyResponse;
import java.time.LocalDateTime;

/**
 * QR Passport 공개 응답.
 *
 * <p><b>{@code productItemId}·{@code productModelId} 를 담지 않는다.</b> 둘 다 순번이라,
 * 담으면 {@code qrToken} 을 우회해 {@code /api/v1/product-models/{id}} 같은 인증 경로를
 * 순서대로 훑어볼 실마리가 된다. 사람이 읽는 {@code itemCode} 만 내려준다.
 *
 * <p>{@code journey}는 생산 배치와 소재 혈통이 정상적으로 연결된 개체에만 채워진다.
 * 마이그레이션 이전의 불완전 데이터는 제품 정보까지 실패시키지 않고
 * {@code traceabilityAvailable=false}로 구분한다.
 */
public record PassportResponse(
        String itemCode,
        int serialNumber,
        ProductItemStatus itemStatus,
        LocalDateTime issuedAt,
        PassportProductSummary product,
        boolean traceabilityAvailable,
        ProductJourneyResponse journey) {

    public static PassportResponse of(
            ProductItem item,
            ProductModel model,
            String brandName,
            ProductJourneyResponse journey) {
        return new PassportResponse(
                item.getItemCode(),
                item.getSerialNumber(),
                item.getStatus(),
                item.getCreatedAt(),
                PassportProductSummary.of(model, brandName, imageUrl(item)),
                journey != null,
                journey);
    }

    /**
     * 대표 이미지는 저장 키가 아니라 공개 경로로 내려준다. 저장 키에는 브랜드 내부 id 가
     * 들어 있고, 화면이 토큰에 접두어를 붙여 주소를 만들면 그 규칙이 두 곳으로 갈라진다.
     */
    private static String imageUrl(ProductItem item) {
        return "/api/v1/qr/" + item.getQrToken() + "/thumbnail";
    }
}
