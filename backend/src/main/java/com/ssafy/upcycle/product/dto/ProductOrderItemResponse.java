package com.ssafy.upcycle.product.dto;

import com.ssafy.upcycle.product.entity.ProductCategory;

/**
 * 주문한 제품 개체 한 점. 내부 PK와 QR 토큰은 노출하지 않고 사용할 URL만 제공한다.
 * 결제 전 주문은 {@code journeyUrl} 이 {@code null} 이다.
 */
public record ProductOrderItemResponse(
        String itemCode,
        int serialNumber,
        String productName,
        ProductCategory category,
        long price,
        String imageUrl,
        String journeyUrl) {
}
