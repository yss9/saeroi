package com.ssafy.upcycle.product.dto;

import com.ssafy.upcycle.product.entity.ProductOrderStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 소비자가 보는 본인 주문. 결제 승인 키와 배송지 같은 민감 정보는 목록에 싣지 않는다.
 *
 * <p><b>{@code orderId} 와 {@code orderCode} 를 둘 다 싣는다.</b> 같은 주문을 가리키는 값이
 * 둘이라 중복처럼 보이지만 쓰임이 다르다 — {@code orderId} 는 결제·취소 경로가 받는 값이고
 * ({@code /product-orders/\{orderId\}/payment/ready}), {@code orderCode} 는 사람이 읽고 문의할 때
 * 대는 값이다. 코드만 내려보내면 주문을 만든 직후 결제를 이어갈 번호를 알 수 없다.
 */
public record ProductOrderResponse(
        Long orderId,
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
