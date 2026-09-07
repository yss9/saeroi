package com.ssafy.upcycle.trade.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ssafy.upcycle.material.dto.IntegralLongDeserializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 결제 승인 통보.
 *
 * <p><b>지금은 클라이언트가 승인 결과를 알려주는 구조다.</b> PG 연동 전이라 그렇다.
 * 실제 KakaoPay 를 붙이면 이 값들은 서버가 PG 서버에 직접 물어서 채워야 하고, 이 요청 바디는
 * {@code pgToken} 하나로 줄어든다. 그 전까지는 <b>금액을 반드시 대조</b>해서
 * 임의 금액 승인을 막는다 ({@code PAYMENT_AMOUNT_MISMATCH}).
 *
 * <p>{@code paymentReference} 는 PG 의 승인 tid 다. Mock 결제도 값을 채운다 — 비어 있으면
 * "결제를 거치지 않은 거래" 와 구분되지 않는다.
 */
public record PaymentConfirmRequest(

        @NotNull(message = "결제 승인 번호는 필수입니다.")
        @Size(max = 100, message = "결제 승인 번호는 100자를 넘을 수 없습니다.")
        String paymentReference,

        @NotNull(message = "승인 금액은 필수입니다.")
        @PositiveOrZero(message = "승인 금액은 0 이상이어야 합니다.")
        @JsonDeserialize(using = IntegralLongDeserializer.class)
        Long paymentAmount) {
}
