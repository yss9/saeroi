package com.ssafy.upcycle.trade.dto;

import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.trade.entity.MaterialListing;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import com.ssafy.upcycle.user.entity.User;
import java.time.LocalDateTime;

/**
 * 거래 한 건. 목록과 상세가 같은 모양을 쓴다.
 *
 * <p><b>거래 자체가 아닌 값이 섞여 있다</b> — 소재 이름·코드, kg 단가, 양쪽 이름.
 * {@code material_trade} 에 없는 값들이지만 화면이 거래를 보여줄 때 항상 함께 필요하고,
 * 없으면 카드마다 소재와 사용자를 다시 조회하게 된다. 조회 쿼리가 이미 조인하므로
 * 함께 내려보내는 데 추가 비용이 없다.
 *
 * <p>{@code pricePerKg} 는 판매 글에서 온다. {@code material_trade} 에 단가 컬럼이 없고
 * 총액만 있어서인데, 판매 글의 단가가 수정되지 않으므로 (수정 API 가 없다) 지금은 거래
 * 시점의 값과 같다. 수정이 생기면 이 값은 과거를 말하지 못하게 되므로 그때는 거래 쪽에
 * 단가를 저장해야 한다.
 *
 * <p>지갑 주소는 거래 시점 스냅샷이라 사용자가 지갑을 바꿔도 변하지 않는다.
 * 화면의 블록 탐색기 링크가 이 주소를 쓴다.
 *
 * <p>{@code paymentReference} 는 PG 승인 tid 다. 결제 전에는 {@code null} 이고,
 * 이 필드가 채워졌는지가 "결제가 실제로 승인되었는가" 의 답이다 — {@code status} 만으로는
 * 판단할 수 없다.
 *
 * <p>{@code buyerSignedAt} 과 {@code receivedAt} 은 다른 값이다. 전자는 Wallet 서명 시각이고
 * 검증 단계 {@code TRANSACTION_VERIFIED} 의 판정 근거다.
 */
public record TradeResponse(
        Long tradeId,
        String tradeCode,
        Long chainTradeId,
        Long listingId,
        Long materialId,
        String materialCode,
        String materialName,
        Long sellerId,
        String sellerName,
        Long buyerId,
        String buyerName,
        String sellerWallet,
        String buyerWallet,
        long quantity,
        long pricePerKg,
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

    public static TradeResponse of(
            MaterialTrade trade,
            Material material,
            MaterialListing listing,
            User seller,
            User buyer) {
        return new TradeResponse(
                trade.getId(),
                trade.getTradeCode(),
                trade.getChainTradeId(),
                trade.getListingId(),
                trade.getMaterialId(),
                material.getMaterialCode(),
                material.getName(),
                trade.getSellerId(),
                seller.getName(),
                trade.getBuyerId(),
                buyer.getName(),
                trade.getSellerWallet(),
                trade.getBuyerWallet(),
                trade.getQuantity(),
                listing.getPricePerKg(),
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
