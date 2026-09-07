package com.ssafy.upcycle.trade.dto;

import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.TradeShipping;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import java.time.LocalDateTime;

/**
 * 발송 처리 결과.
 *
 * <p>{@code shippedAt} 은 거래와 배송 정보 양쪽에 같은 값으로 기록된다. 거래 상태가
 * 넘어간 시각과 물류 기록을 각각 갖는 구조라 두 컬럼이 존재하며, 발송 처리는 한 번에
 * 둘을 남긴다.
 */
public record TradeShipResponse(
        Long tradeId,
        String tradeCode,
        TradeStatus status,
        LocalDateTime shippedAt,
        String carrier,
        String trackingNumber) {

    public static TradeShipResponse of(MaterialTrade trade, TradeShipping shipping) {
        return new TradeShipResponse(
                trade.getId(),
                trade.getTradeCode(),
                trade.getStatus(),
                trade.getShippedAt(),
                shipping.getCarrier(),
                shipping.getTrackingNumber());
    }
}
