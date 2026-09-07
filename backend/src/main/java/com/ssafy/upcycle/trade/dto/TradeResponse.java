package com.ssafy.upcycle.trade.dto;

import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import java.time.LocalDateTime;

/**
 * 거래 한 건.
 *
 * <p>지갑 주소를 그대로 내려보낸다. 거래 시점 스냅샷이라 사용자가 지갑을 바꿔도 이 값은
 * 변하지 않으며, 화면의 블록 탐색기 링크가 이 주소를 쓴다.
 *
 * <p>{@code paymentReference} 는 PG 승인 tid 다. 결제 전에는 {@code null} 이고, 이 필드가
 * 채워졌는지가 "결제가 실제로 승인되었는가" 의 답이다 — {@code status} 만 보면 Mock 결제와
 * 실제 승인을 구분할 수 없다.
 *
 * <p>시각 필드가 여럿인 것은 상태 이력을 화면이 Timeline 으로 그리기 위해서다.
 * {@code buyerSignedAt} 과 {@code receivedAt} 은 다른 값이다 — 전자는 Wallet 서명 시각이고
 * 검증 단계 {@code TRANSACTION_VERIFIED} 의 판정 근거다.
 */
public record TradeResponse(
        Long tradeId,
        String tradeCode,
        Long chainTradeId,
        Long listingId,
        Long materialId,
        Long sellerId,
        Long buyerId,
        String sellerWallet,
        String buyerWallet,
        long quantity,
        long totalPrice,
        PaymentMethod paymentMethod,
        String paymentReference,
        long paymentAmount,
        TradeStatus status,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime shippedAt,
        LocalDateTime receivedAt,
        LocalDateTime buyerSignedAt,
        LocalDateTime completedAt) {

    public static TradeResponse from(MaterialTrade trade) {
        return new TradeResponse(
                trade.getId(),
                trade.getTradeCode(),
                trade.getChainTradeId(),
                trade.getListingId(),
                trade.getMaterialId(),
                trade.getSellerId(),
                trade.getBuyerId(),
                trade.getSellerWallet(),
                trade.getBuyerWallet(),
                trade.getQuantity(),
                trade.getTotalPrice(),
                trade.getPaymentMethod(),
                trade.getPaymentReference(),
                trade.getPaymentAmount(),
                trade.getStatus(),
                trade.getCreatedAt(),
                trade.getPaidAt(),
                trade.getShippedAt(),
                trade.getReceivedAt(),
                trade.getBuyerSignedAt(),
                trade.getCompletedAt());
    }
}
