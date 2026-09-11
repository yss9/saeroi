package com.ssafy.upcycle.production.dto;

import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductItemStatus;
import java.time.LocalDateTime;

/**
 * 확정으로 만들어진 제품 개체 하나. <b>QR 라벨 인쇄가 쓰는 응답이다.</b>
 *
 * <p>{@code qrToken} 을 담는다. 이 값은 인증 없이 열리는 조회 경로의 열쇠라 아무에게나
 * 주면 안 되지만, <b>브랜드는 이 토큰을 물리적으로 제품에 인쇄해야 한다.</b> 그래서 배치
 * 소유자에게만 준다 — 남의 배치를 조회하면 403 이다.
 *
 * <p>{@code qrUrl} 은 QR 이미지가 담고 있는 주소 그 자체다. 화면이 라벨 옆에 텍스트로
 * 보여주거나 직접 그릴 때 쓴다 — 토큰에 접두어를 붙이는 규칙을 프론트가 따로 갖게 하면
 * 설정이 바뀔 때 한쪽만 바뀐다.
 *
 * <p>{@code productModelId} 는 담지 않는다. 배치 조회의 응답이고 배치가 이미 그 값을
 * 갖고 있어, 개체마다 반복하면 같은 값이 개수만큼 실린다.
 */
public record ProductionItemResponse(
        Long productItemId,
        String itemCode,
        int serialNumber,
        String qrToken,
        String qrUrl,
        ProductItemStatus status,
        LocalDateTime createdAt) {

    /**
     * @param qrUrl QR 이미지가 담고 있는 주소. 서버가 조립한 값을 그대로 내려보낸다 —
     *     화면이 토큰에 접두어를 붙여 만들면 그 규칙이 두 곳이 되고, 언젠가 한쪽만 바뀐다
     */
    public static ProductionItemResponse of(ProductItem item, String qrUrl) {
        return new ProductionItemResponse(
                item.getId(),
                item.getItemCode(),
                item.getSerialNumber(),
                item.getQrToken(),
                qrUrl,
                item.getStatus(),
                item.getCreatedAt());
    }
}
