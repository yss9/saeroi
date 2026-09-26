package com.ssafy.upcycle.product.dto;

import com.ssafy.upcycle.product.entity.ProductShipping;
import java.time.LocalDateTime;

/**
 * 어디로, 누구에게 보내는가. 그리고 이미 보냈다면 어떤 송장으로.
 *
 * <p><b>수령인이 주문자와 다를 수 있다.</b> 송장에 쓸 이름과 연락처는 언제나 이쪽이다 —
 * 선물이나 가족 집으로 보내는 주문에서 주문자 이름을 적으면 물건이 엉뚱한 사람 앞으로 간다.
 *
 * <p>주문 시점에 얼려 둔 값이다. 구매자가 나중에 주소록을 고쳐도 이미 들어온 주문의 배송지는
 * 바뀌지 않는다.
 *
 * <p>{@code carrier}/{@code trackingNumber}/{@code shippedAt} 은 발송 전에는 비어 있다.
 * 셋 다 비어 있으면 아직 안 보낸 주문이다.
 */
public record BrandProductOrderShippingResponse(
        String receiverName,
        String receiverPhone,
        String zipCode,
        String address,
        String addressDetail,
        String carrier,
        String trackingNumber,
        LocalDateTime shippedAt) {

    public static BrandProductOrderShippingResponse from(ProductShipping shipping) {
        return new BrandProductOrderShippingResponse(
                shipping.getReceiverName(),
                shipping.getReceiverPhone(),
                shipping.getZipCode(),
                shipping.getAddress(),
                shipping.getAddressDetail(),
                shipping.getCarrier(),
                shipping.getTrackingNumber(),
                shipping.getShippedAt());
    }
}
