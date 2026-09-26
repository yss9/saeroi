package com.ssafy.upcycle.product.dto;

import com.ssafy.upcycle.product.entity.ProductOrderStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 브랜드에게 들어온 주문 한 건. {@code GET /api/v1/product-orders/brand/me} 의 데이터다.
 *
 * <p>구매자가 보는 {@code ProductOrderResponse} 와 <b>다른 것을 싣는다.</b> 브랜드가 이
 * 화면에서 하는 일은 하나 — 물건을 싸서 보내는 것이다. 그래서 <b>배송지가 들어 있고</b>,
 * 대신 제품 여정 주소처럼 구매자용 값은 없다.
 *
 * <p><b>배송지는 여기서만 나간다.</b> 발송 처리 응답({@code ProductOrderShipResponse})에는
 * 일부러 넣지 않았다 — 브랜드는 이 목록에서 이미 받았고, 응답마다 다시 실으면 로그와
 * 캐시에 개인정보가 여러 벌 남는다.
 *
 * <p>{@code buyerName} 은 주문한 계정의 이름이고 {@code shipping.receiverName} 과 다를 수
 * 있다 — 선물이나 가족 집으로 보내는 경우다. 송장에 쓸 이름은 <b>수령인</b> 쪽이다.
 */
public record BrandProductOrderResponse(
        Long orderId,
        String orderCode,
        ProductOrderStatus status,
        String buyerName,
        long totalPrice,
        LocalDateTime orderedAt,
        LocalDateTime paidAt,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt,
        List<BrandProductOrderItemResponse> items,
        BrandProductOrderShippingResponse shipping) {
}
