package com.ssafy.upcycle.trade.payment;

/**
 * 결제 준비 요청.
 *
 * <p>{@code orderId}·{@code userId} 는 PG 가 우리 주문을 식별하는 문자열이다. 승인 단계에서
 * <b>준비 때와 똑같은 값</b>을 보내야 하며, 다르면 카카오페이가 거절한다. 그래서 거래 코드와
 * 사용자 id 처럼 <b>바뀌지 않는 값</b>을 쓴다.
 *
 * @param orderId    우리 주문 식별자 (거래 코드)
 * @param userId     우리 사용자 식별자 (구매자 id)
 * @param itemName   결제창에 보일 상품명
 * @param totalPrice 총 결제 금액 (원)
 * @param returnUrl  결제창에서 돌아올 주소. 거래마다 달라야 어느 거래의 승인인지 알 수 있다
 */
public record PaymentReadyCommand(
        String orderId, String userId, String itemName, long totalPrice, String returnUrl) {
}
