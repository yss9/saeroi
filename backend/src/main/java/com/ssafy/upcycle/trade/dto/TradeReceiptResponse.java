package com.ssafy.upcycle.trade.dto;

import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import java.time.LocalDateTime;

/**
 * 수령 확인 결과.
 *
 * <p>양쪽 보유량을 함께 내려준다. 이 요청의 핵심이 소유권 이전이라, 얼마가 옮겨졌고
 * 각자 얼마가 남았는지가 곧 결과다.
 *
 * <p>수량 단위는 gram 이다.
 */
public record TradeReceiptResponse(
        Long tradeId,
        String tradeCode,
        TradeStatus status,
        LocalDateTime receivedAt,
        LocalDateTime buyerSignedAt,
        LocalDateTime completedAt,
        long transferredQuantity,
        long sellerQuantity,
        long buyerQuantity) {

    public static TradeReceiptResponse of(
            MaterialTrade trade, MaterialBalance sellerBalance, MaterialBalance buyerBalance) {
        return new TradeReceiptResponse(
                trade.getId(),
                trade.getTradeCode(),
                trade.getStatus(),
                trade.getReceivedAt(),
                trade.getBuyerSignedAt(),
                trade.getCompletedAt(),
                trade.getQuantity(),
                sellerBalance.getQuantity(),
                buyerBalance.getQuantity());
    }
}
