package com.ssafy.upcycle.trade.payment;

/**
 * 결제 준비 결과.
 *
 * @param tid         PG 거래 식별자. 승인·취소에 계속 쓰이므로 저장해야 한다
 * @param redirectUrl 사용자를 보낼 결제창 주소. Mock 구현은 빈 문자열을 준다
 */
public record PaymentReady(String tid, String redirectUrl) {
}
