package com.ssafy.upcycle.trade.dto;

import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import java.time.LocalDateTime;

/**
 * 수령 확인 결과.
 *
 * <p>이 요청의 핵심이 소유권 이전이라, 얼마가 옮겨졌고 구매자에게 얼마가 쌓였는지를
 * 함께 내려준다.
 *
 * <p><b>판매자의 보유량은 내려주지 않는다.</b> 이 응답을 받는 사람은 구매자이고,
 * 판매자가 그 소재를 통틀어 얼마나 갖고 있는지는 이 거래와 무관한 재고 정보다.
 * 거래 결과를 확인하는 데 필요하지 않은 값을 상대방에게 알려줄 이유가 없다.
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
        long buyerQuantity) {

    public static TradeReceiptResponse of(MaterialTrade trade, MaterialBalance buyerBalance) {
        return new TradeReceiptResponse(
                trade.getId(),
                trade.getTradeCode(),
                trade.getStatus(),
                trade.getReceivedAt(),
                trade.getBuyerSignedAt(),
                trade.getCompletedAt(),
                trade.getQuantity(),
                buyerBalance.getQuantity());
    }
}
