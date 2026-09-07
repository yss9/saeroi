package com.ssafy.upcycle.trade.dto;

import com.ssafy.upcycle.trade.entity.TradeShipping;
import java.time.LocalDateTime;

/**
 * 거래의 배송 정보.
 *
 * <p>두 가지를 답한다 — <b>어디로 보내는가</b>(수취인·주소)와 <b>어떻게 갔는가</b>(송장·시각).
 * 앞의 것은 판매자가 발송 화면에서 필요하고, 뒤의 것은 발송 뒤 상세를 다시 열었을 때 필요하다.
 * 발송 응답으로 한 번 내려주는 것만으로는 새로고침하면 사라진다.
 *
 * <p>송장 관련 값은 발송 전까지 전부 {@code null} 이다. 발송했더라도 직접 수령·화물처럼
 * 송장이 없는 전달이면 {@code carrier} 와 {@code trackingNumber} 는 계속 비어 있다 —
 * {@code shippedAt} 이 발송 여부의 기준이다.
 */
public record TradeShippingResponse(
        String receiverName,
        String receiverPhone,
        String zipCode,
        String address,
        String addressDetail,
        String carrier,
        String trackingNumber,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt) {

    /**
     * 배송 정보가 없는 거래는 {@code null} 을 돌려준다.
     *
     * <p>이 변경 이전에 만들어진 거래에는 배송 행이 없다. 빈 객체를 만들어 내려보내면
     * 화면이 "주소가 비어 있는 배송지" 로 잘못 읽는다.
     */
    public static TradeShippingResponse from(TradeShipping shipping) {
        if (shipping == null) {
            return null;
        }
        return new TradeShippingResponse(
                shipping.getReceiverName(),
                shipping.getReceiverPhone(),
                shipping.getZipCode(),
                shipping.getAddress(),
                shipping.getAddressDetail(),
                shipping.getCarrier(),
                shipping.getTrackingNumber(),
                shipping.getShippedAt(),
                shipping.getDeliveredAt());
    }
}
