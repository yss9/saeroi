package com.ssafy.upcycle.trade.payment;

import java.time.LocalDateTime;

/**
 * 결제 승인 결과.
 *
 * <p>{@code approvedAmount} 를 호출부가 거래 금액과 <b>반드시 대조</b>한다. PG 응답을 그대로
 * 믿고 기록하면, 금액이 어긋난 승인이 정상 결제로 남는다.
 */
public record PaymentApproval(String tid, long approvedAmount, LocalDateTime approvedAt) {
}
