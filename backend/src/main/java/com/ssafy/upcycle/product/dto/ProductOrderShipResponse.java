package com.ssafy.upcycle.product.dto;

import com.ssafy.upcycle.product.entity.ProductOrder;
import com.ssafy.upcycle.product.entity.ProductOrderStatus;
import com.ssafy.upcycle.product.entity.ProductShipping;
import java.time.LocalDateTime;

/**
 * 발송 처리 결과. 브랜드가 본다.
 *
 * <p>{@code ProductOrderResponse} 를 쓰지 않는 이유는 그것이 <b>구매자가 보는 화면</b>의
 * 자료이기 때문이다. 그쪽은 제품 이름·여정 주소처럼 브랜드에게 필요 없는 값을 싣고,
 * 조립 경로도 구매자 본인 확인을 거친다. 발송 화면이 확인해야 하는 것은 "언제, 어떤 송장으로
 * 나갔는가" 다.
 *
 * <p>수령인 이름이나 주소는 싣지 않는다. 브랜드는 발송 시점에 이미 그 값을 알고 있고,
 * 발송 응답에 다시 실으면 로그나 캐시에 개인정보가 한 벌 더 남는다.
 */
public record ProductOrderShipResponse(
        Long orderId,
        String orderCode,
        ProductOrderStatus status,
        LocalDateTime shippedAt,
        String carrier,
        String trackingNumber) {

    public static ProductOrderShipResponse of(ProductOrder order, ProductShipping shipping) {
        return new ProductOrderShipResponse(
                order.getId(),
                order.getOrderCode(),
                order.getStatus(),
                order.getShippedAt(),
                shipping.getCarrier(),
                shipping.getTrackingNumber());
    }
}
