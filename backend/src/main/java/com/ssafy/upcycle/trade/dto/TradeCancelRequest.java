package com.ssafy.upcycle.trade.dto;

import jakarta.validation.constraints.Size;

/**
 * 거래 취소 요청.
 *
 * <p><b>사유는 선택이다.</b> 결제창을 닫고 나온 사용자에게 이유를 적으라고 요구하면
 * 취소가 막히거나 의미 없는 문자열이 쌓인다. 판매자가 재고 문제로 취소하는 경우처럼
 * 상대가 알아야 하는 상황에서 채우면 된다.
 *
 * <p>바디 자체를 생략할 수 있게 해 두었다 — 기존 호출부가 바디 없이 부르고 있었고,
 * 사유가 없는 취소는 앞으로도 정상 경로다.
 */
public record TradeCancelRequest(

        @Size(max = 255, message = "취소 사유는 255자를 넘을 수 없습니다.")
        String reason) {

    /** 바디를 보내지 않은 요청. */
    public static TradeCancelRequest empty() {
        return new TradeCancelRequest(null);
    }
}
