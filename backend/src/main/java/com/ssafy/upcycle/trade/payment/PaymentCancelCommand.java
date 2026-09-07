package com.ssafy.upcycle.trade.payment;

/**
 * 결제 취소(환불) 요청.
 *
 * <p>{@code cancelAmount} 는 전액이다. 부분 환불은 정책 v3 의 MVP 범위 밖이며
 * ({@code PARTIAL_REFUND} 폐지), 지금은 거래 단위로만 취소한다.
 */
public record PaymentCancelCommand(String tid, long cancelAmount) {
}
