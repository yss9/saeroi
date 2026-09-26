package com.ssafy.upcycle.trade.service;

import com.ssafy.upcycle.notification.entity.NotificationReferenceType;
import com.ssafy.upcycle.notification.entity.NotificationType;
import com.ssafy.upcycle.notification.service.NotificationCommand;
import com.ssafy.upcycle.notification.service.NotificationService;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TradeNotificationService {

    private final NotificationService notifications;

    public void created(MaterialTrade trade) {
        send(trade.getSellerId(), NotificationType.TRADE_CREATED,
                "새 소재 구매 요청이 도착했어요", "구매 요청과 배송 정보를 확인해 주세요.", trade);
    }

    public void paid(MaterialTrade trade) {
        send(trade.getSellerId(), NotificationType.TRADE_PAID,
                "소재 결제가 완료됐어요", "결제된 소재의 발송을 준비해 주세요.", trade);
    }

    public void canceled(MaterialTrade trade, Long actorId) {
        Long recipientId = trade.getSellerId().equals(actorId)
                ? trade.getBuyerId()
                : trade.getSellerId();
        send(recipientId, NotificationType.TRADE_CANCELED,
                "소재 거래가 취소됐어요", "취소된 거래 상태를 확인해 주세요.", trade);
    }

    public void shipped(MaterialTrade trade) {
        send(trade.getBuyerId(), NotificationType.TRADE_SHIPPED,
                "구매한 소재가 발송됐어요", "배송 정보를 확인해 주세요.", trade);
    }

    public void received(MaterialTrade trade) {
        send(trade.getSellerId(), NotificationType.TRADE_RECEIVED,
                "구매자가 소재를 수령했어요", "완료된 거래를 확인해 주세요.", trade);
    }

    private void send(
            Long recipientId,
            NotificationType type,
            String title,
            String message,
            MaterialTrade trade) {
        notifications.notify(new NotificationCommand(
                recipientId,
                type,
                title,
                message,
                "/trades/" + trade.getId(),
                NotificationReferenceType.MATERIAL_TRADE,
                trade.getId(),
                type + ":" + trade.getId() + ":" + recipientId));
    }
}
