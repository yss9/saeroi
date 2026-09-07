package com.ssafy.upcycle.trade.payment;

/**
 * 결제 승인 요청.
 *
 * <p>{@code orderId}·{@code userId} 는 준비 때 보낸 값과 같아야 한다. 다르면 카카오페이가
 * 거절한다.
 *
 * @param pgToken        결제창이 복귀 URL 로 돌려주는 인증 토큰
 * @param expectedAmount 우리가 청구했다고 믿는 금액 (원).
 *                       <b>실제 PG 는 이 값을 쓰지 않는다</b> — 승인 응답의 금액이 사실이고
 *                       호출부가 그것과 거래 금액을 대조한다. {@code MockPaymentGateway} 만
 *                       이 값을 그대로 승인 금액으로 되돌려주어, 대조 코드가 Mock 경로에서도
 *                       똑같이 실행되게 한다
 */
public record PaymentApproveCommand(
        String tid, String orderId, String userId, String pgToken, long expectedAmount) {
}
