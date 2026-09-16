package com.ssafy.upcycle.product.dto;

import com.ssafy.upcycle.product.entity.ProductShipping;
import java.time.LocalDateTime;

/**
 * 구매자가 보는 배송 정보.
 *
 * <p><b>수령인·주소를 싣는다(2026-09-16 결정 변경).</b> 마이페이지 주문 내역에 "배송조회"
 * 화면을 새로 붙이면서, 본인이 결제한 자기 주문의 배송지를 스스로 다시 볼 수 있어야 한다는
 * 요구가 생겼다 — 원래는 "본인이 고른 주소라 다시 보여줄 이유가 적다"고 판단해 뺐었지만,
 * 그건 "주문 목록에 늘 들고 다닐 이유"에 대한 것이었고 "배송조회를 누른 사람에게 보여줄
 * 이유"는 다르다. 로그·캐시에 개인정보가 남는 문제는 여전해 남지만, 어차피 본인 소유
 * 주문에 한해서만(JWT 소유자 검증) 내려가는 응답이라 노출 범위가 넓어지지 않는다.
 *
 * <p>브랜드용 {@code BrandProductOrderShippingResponse} 와 이름이 비슷하지만 담는 것이 다르다.
 * 그쪽은 <b>보내기 위해</b> 주소가 필요하고, 이쪽은 <b>어디로 갔는지</b> 본인이 확인한다.
 *
 * <p>{@code shippedAt} 은 {@code ProductOrderResponse.shippedAt} 과 <b>같은 값</b>이다. 두
 * 곳에 있는 것이 중복처럼 보이지만, 발송 시각은 주문의 사건이면서 동시에 이 송장의 사건이라
 * 양쪽이 각자 온전한 모양을 갖는 편이 화면에서 다루기 쉽다. 발송 처리가 두 값을 같은
 * 트랜잭션에서 같은 시각으로 적으므로 어긋나지 않는다.
 *
 * <p>{@code carrier}/{@code trackingNumber}/{@code shippedAt} 이 전부 {@code null} 이면 아직
 * 보내지 않은 주문이다. 송장 없이 전달하는 방식도 있어 {@code shippedAt} 만 있고 나머지가
 * 비어 있을 수 있다. 수령인·주소는 주문 시점에 확정되므로 항상 값이 있다.
 */
public record ProductOrderShippingResponse(
        String receiverName,
        String zipCode,
        String address,
        String addressDetail,
        String carrier,
        String trackingNumber,
        LocalDateTime shippedAt) {

    public static ProductOrderShippingResponse from(ProductShipping shipping) {
        return new ProductOrderShippingResponse(
                shipping.getReceiverName(),
                shipping.getZipCode(),
                shipping.getAddress(),
                shipping.getAddressDetail(),
                shipping.getCarrier(),
                shipping.getTrackingNumber(),
                shipping.getShippedAt());
    }
}
