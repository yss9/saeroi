package com.ssafy.upcycle.product.dto;

import jakarta.validation.constraints.Size;

/**
 * 주문 취소 요청.
 *
 * <p>사유는 선택이다. 결제창을 닫고 화면이 정리를 부르는 경로처럼 사람이 적은 사유가 없는
 * 경우가 있고, 그때 사유를 요구하면 화면이 의미 없는 문자열을 지어내게 된다.
 */
public record ProductOrderCancelRequest(

        @Size(max = 255, message = "취소 사유는 255자를 넘을 수 없습니다.")
        String reason) {
}
