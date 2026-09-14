package com.ssafy.upcycle.trade.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.entity.MaterialEventReferenceType;
import com.ssafy.upcycle.material.entity.MaterialEventType;
import com.ssafy.upcycle.material.service.MaterialEventRecorder;
import com.ssafy.upcycle.trade.TradeErrorCode;
import com.ssafy.upcycle.trade.dto.TradeShipRequest;
import com.ssafy.upcycle.trade.dto.TradeShipResponse;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.TradeShipping;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
import com.ssafy.upcycle.trade.repository.TradeShippingRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TradeShipmentService {

    private final MaterialTradeRepository materialTradeRepository;
    private final TradeShippingRepository tradeShippingRepository;
    private final Clock clock;
    private final MaterialEventRecorder eventRecorder;
    private final TradeNotificationService businessNotifications;

    /**
     * 판매자가 소재를 발송한다. 2-Track 중 보낸 측의 기록이다.
     *
     * <p><b>잠금은 건드리지 않는다.</b> MARKETPLACE 잠금은 배송 중에도 유지되어야 한다 —
     * 풀어버리면 판매자가 배송 중인 수량을 다른 곳에 또 팔 수 있다. 잠금이 소비되는 시점은
     * 구매자 수령 확인이다.
     *
     * <p>보유량도 그대로다. 소유권은 발송이 아니라 수령 확인 시점에 넘어간다. 보냈다는
     * 사실만으로는 받았다는 보장이 없다.
     */
    @Transactional
    public TradeShipResponse ship(Long sellerId, Long tradeId, TradeShipRequest request) {
        MaterialTrade trade = materialTradeRepository.findById(tradeId)
                .orElseThrow(() -> new BusinessException(TradeErrorCode.TRADE_NOT_FOUND));

        requireSeller(trade, sellerId);
        requireShippable(trade);
        requireCarrierWithTrackingNumber(request);

        TradeShipping shipping = tradeShippingRepository.findByTradeId(tradeId)
                .orElseThrow(() -> new BusinessException(TradeErrorCode.TRADE_SHIPPING_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now(clock);
        trade.ship(now);
        shipping.registerShipment(request.carrier(), request.trackingNumber(), now);
        eventRecorder.record(
                trade.getMaterialId(), MaterialEventType.TRADE_SHIPPED, sellerId,
                MaterialEventReferenceType.MATERIAL_TRADE, trade.getId(), null,
                trade.getQuantity(), "판매자가 소재 발송",
                MaterialEventRecorder.key(
                        MaterialEventType.TRADE_SHIPPED, trade.getMaterialId(), trade.getId()));
        businessNotifications.shipped(trade);

        return TradeShipResponse.of(trade, shipping);
    }

    /** 남의 거래를 발송 처리할 수 없다. 구매자도 마찬가지다 — 보내는 쪽은 판매자다. */
    private void requireSeller(MaterialTrade trade, Long sellerId) {
        if (!trade.getSellerId().equals(sellerId)) {
            throw new BusinessException(TradeErrorCode.NOT_TRADE_SELLER);
        }
    }

    /**
     * 상태를 엔티티에 맡기지 않고 여기서 먼저 본다. 엔티티는 {@code IllegalStateException}
     * 을 던지는데 그대로 두면 500 이 나간다. 엔티티 쪽 검사는 최후 방어선으로 남긴다.
     */
    private void requireShippable(MaterialTrade trade) {
        if (trade.getStatus() == TradeStatus.SHIPPED) {
            throw new BusinessException(TradeErrorCode.TRADE_ALREADY_SHIPPED);
        }
        if (trade.getStatus() != TradeStatus.PAID) {
            throw new BusinessException(TradeErrorCode.TRADE_NOT_SHIPPABLE);
        }
    }

    /** 택배사 없는 운송장 번호는 조회할 수 없어 기록해도 쓸모가 없다. */
    private void requireCarrierWithTrackingNumber(TradeShipRequest request) {
        if (StringUtils.hasText(request.trackingNumber())
                && !StringUtils.hasText(request.carrier())) {
            throw new BusinessException(TradeErrorCode.TRADE_CARRIER_REQUIRED);
        }
    }
}
