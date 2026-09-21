package com.ssafy.upcycle.trade.service;

import com.ssafy.upcycle.blockchain.service.ChainOrderStatusReader;
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
    private final ChainOrderStatusReader chainOrders;

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
    /**
     * 발송할 수 있는 상태인가.
     *
     * <p><b>온체인 주문이 먼저다.</b> 컨트랙트의 {@code markShipped(orderId)} 는 주문 번호
     * 없이는 부를 수도 없어서, 이 검사가 없으면 서버만 앞서 나간다. 실제로 그렇게 새어
     * 나갔다 — 결제 뒤 주문 서명을 건너뛴 거래가 발송·수령까지 끝나 DB 는 소재를 넘겨
     * 줬는데 체인은 그 거래를 모르는 채로 굳었고, 그 소재를 가공에 보내려 할 때
     * insufficient material 로 거절당해서야 드러났다.
     *
     * <p>그 시점에는 되돌릴 방법이 없다. 온체인 기록은 {@code PAID} 구간에서만 남길 수
     * 있는데 거래는 이미 그 구간을 지나쳤기 때문이다. 그래서 지나가기 전에 막는다.
     *
     * <p><b>발송 서명도 먼저여야 한다.</b> 가공·수령과 같은 순서다 — 서명이 체인에 남은
     * 뒤에 DB 가 따라간다. 다만 체인을 쓰지 않는 환경은 구분한다.
     */
    private void requireShippable(MaterialTrade trade) {
        if (trade.getStatus() == TradeStatus.SHIPPED) {
            throw new BusinessException(TradeErrorCode.TRADE_ALREADY_SHIPPED);
        }
        if (trade.getStatus() != TradeStatus.PAID) {
            throw new BusinessException(TradeErrorCode.TRADE_NOT_SHIPPABLE);
        }
        if (!chainOrders.canRead()) {
            // 체인을 아예 쓰지 않는 환경이다(설정이 비어 있다). 어긋날 대상이 없으므로
            // 막지 않는다 — 막으면 로컬·테스트에서 발송이 통째로 불가능해진다.
            return;
        }
        if (trade.getChainTradeId() == null) {
            throw new BusinessException(TradeErrorCode.TRADE_CHAIN_ORDER_REQUIRED);
        }
        // **서명이 먼저다.** 체인에 발송이 남기 전에 DB 를 밀면, 판매자가 서명 창을 끈
        // 경우 DB 는 SHIPPED 인데 체인은 CREATED 로 남는다. 그러면 구매자가 수령 서명을
        // 할 수 없다 — 컨트랙트가 체인이 SHIPPED 일 때만 수령을 받는다.
        if (!chainOrders.alreadyShipped(trade.getChainTradeId())) {
            throw new BusinessException(TradeErrorCode.TRADE_CHAIN_SHIPMENT_REQUIRED);
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
