package com.ssafy.upcycle.passport.dto;

import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductItemStatus;
import com.ssafy.upcycle.product.entity.ProductModel;
import java.time.LocalDateTime;

/**
 * QR Passport 공개 응답.
 *
 * <p><b>{@code productItemId}·{@code productModelId} 를 담지 않는다.</b> 둘 다 순번이라,
 * 담으면 {@code qrToken} 을 우회해 {@code /api/v1/product-models/{id}} 같은 인증 경로를
 * 순서대로 훑어볼 실마리가 된다. 사람이 읽는 {@code itemCode} 만 내려준다.
 *
 * <p><b>{@code traceabilityAvailable} 이 지금은 언제나 {@code false} 다.</b> 원재료 출처·
 * 가공 이력을 이으려면 Production 도메인(생산 배치·투입·산출)이 있어야 하는데 아직 없다
 * ({@code product} 패키지에는 {@code package-info.java} 뿐이다). 그 도메인이 생기기 전에
 * 이 필드를 지우지 않고 얹어 둔 것은, 나중에 값이 채워질 때 프론트가 이미 그 분기를
 * 갖고 있게 하기 위해서다 — 필드를 나중에 추가하는 것보다 값만 바뀌는 쪽이 안전하다.
 */
public record PassportResponse(
        String itemCode,
        int serialNumber,
        ProductItemStatus itemStatus,
        LocalDateTime issuedAt,
        PassportProductSummary product,
        boolean traceabilityAvailable) {

    public static PassportResponse of(ProductItem item, ProductModel model) {
        return new PassportResponse(
                item.getItemCode(),
                item.getSerialNumber(),
                item.getStatus(),
                item.getCreatedAt(),
                PassportProductSummary.of(model),
                false);
    }
}
