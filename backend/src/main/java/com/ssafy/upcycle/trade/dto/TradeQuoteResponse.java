package com.ssafy.upcycle.trade.dto;

/**
 * 구매 전 금액 미리보기.
 *
 * <p>화면도 {@code calculateTotalPrice} 로 같은 계산을 한다. 그런데도 이 엔드포인트가 있는
 * 이유는 <b>단가와 잔여 수량이 그 사이에 바뀔 수 있기 때문</b>이다. 화면이 목록을 읽은 뒤
 * 다른 구매자가 먼저 사면, 화면의 계산은 이미 없는 수량에 대한 금액이다.
 * 여기서 내려주는 {@code availableQuantity} 가 지금 시점의 답이다.
 *
 * <p>이 응답은 <b>약속이 아니다.</b> 실제 금액은 구매 요청이 만들어지는 순간 다시 계산된다.
 */
public record TradeQuoteResponse(
        Long listingId,
        long quantity,
        long pricePerKg,
        long totalPrice,
        long availableQuantity,
        boolean purchasable) {
}
