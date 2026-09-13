package com.ssafy.upcycle.product.dto;

import com.ssafy.upcycle.product.entity.ProductOrderStatus;
import java.time.LocalDateTime;
import java.util.List;

/** 소비자가 보는 본인 주문. 결제 승인 키와 배송지 같은 민감 정보는 목록에 싣지 않는다. */
public record ProductOrderResponse(
        String orderCode,
        String brandName,
        long totalPrice,
        ProductOrderStatus status,
        LocalDateTime orderedAt,
        LocalDateTime paidAt,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt,
        List<ProductOrderItemResponse> items) {
}
