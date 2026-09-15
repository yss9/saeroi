package com.ssafy.upcycle.product.dto;

import com.ssafy.upcycle.product.entity.ProductShipping;
import java.time.LocalDateTime;

/**
 * 구매자가 보는 배송 정보. <b>송장뿐이다.</b>
 *
 * <p>수령인·주소는 싣지 않는다. 본인이 고른 주소라 화면이 다시 보여줄 이유가 적고, 응답마다
 * 실으면 로그와 캐시에 개인정보가 여러 벌 남는다. "어디로 가는지" 까지 보여주게 되면 그때
 * 넓히면 된다 — 한 번 내보낸 필드는 줄이기 어렵지만 늘리는 것은 쉽다.
 *
 * <p>브랜드용 {@code BrandProductOrderShippingResponse} 와 이름이 비슷하지만 담는 것이 다르다.
 * 그쪽은 <b>보내기 위해</b> 주소가 필요하고, 이쪽은 <b>어디쯤 왔는지</b> 알면 된다.
 *
 * <p>{@code shippedAt} 은 {@code ProductOrderResponse.shippedAt} 과 <b>같은 값</b>이다. 두
 * 곳에 있는 것이 중복처럼 보이지만, 발송 시각은 주문의 사건이면서 동시에 이 송장의 사건이라
 * 양쪽이 각자 온전한 모양을 갖는 편이 화면에서 다루기 쉽다. 발송 처리가 두 값을 같은
 * 트랜잭션에서 같은 시각으로 적으므로 어긋나지 않는다.
 *
 * <p>셋 다 {@code null} 이면 아직 보내지 않은 주문이다. 송장 없이 전달하는 방식도 있어
 * {@code shippedAt} 만 있고 {@code carrier}/{@code trackingNumber} 는 비어 있을 수 있다.
 */
public record ProductOrderShippingResponse(
        String carrier, String trackingNumber, LocalDateTime shippedAt) {

    public static ProductOrderShippingResponse from(ProductShipping shipping) {
        return new ProductOrderShippingResponse(
                shipping.getCarrier(), shipping.getTrackingNumber(), shipping.getShippedAt());
    }
}
