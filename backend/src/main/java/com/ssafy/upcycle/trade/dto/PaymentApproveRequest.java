package com.ssafy.upcycle.trade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 결제 승인 요청.
 *
 * <p><b>금액을 받지 않는다.</b> 이전 구조에서는 클라이언트가 승인 금액을 알려주고 서버가
 * 대조했는데, 이제 서버가 PG 에 직접 물어보므로 클라이언트가 금액을 말할 이유가 없다.
 * 받아 두면 언젠가 그 값을 믿는 코드가 생긴다.
 *
 * <p>{@code tid} 도 받지 않는다. 준비 단계에서 서버가 거래에 저장해 두었고, 클라이언트가
 * 보낸 tid 를 쓰면 남의 결제 번호로 승인을 시도할 수 있다.
 *
 * @param pgToken 결제창이 복귀 URL 의 {@code pg_token} 쿼리로 돌려주는 인증 토큰
 */
public record PaymentApproveRequest(

        @NotBlank(message = "결제 인증 토큰이 필요합니다.")
        @Size(max = 200, message = "결제 인증 토큰이 너무 깁니다.")
        String pgToken) {
}
