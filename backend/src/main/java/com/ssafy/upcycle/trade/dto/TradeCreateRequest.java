package com.ssafy.upcycle.trade.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ssafy.upcycle.material.dto.IntegralLongDeserializer;
import com.ssafy.upcycle.material.dto.MaterialQuantityUnit;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 구매 요청. <b>부분 구매를 전제로 한다</b> — 판매 글의 전량이 아니라 원하는 수량을 보낸다.
 *
 * <p>{@code totalPrice} 를 받지 않는 것이 핵심이다. 금액을 클라이언트가 정하면 1 원짜리
 * 요청을 만들 수 있다. 서버가 판매 글의 {@code pricePerKg} 로 다시 계산하고, 화면이 미리
 * 보여준 금액은 응답의 {@code totalPrice} 와 대조만 한다.
 *
 * <p>{@code buyerId} 도 받지 않는다. JWT {@code sub} 에서 얻는다.
 */
public record TradeCreateRequest(

        @NotNull(message = "판매 글은 필수입니다.")
        Long listingId,

        @NotNull(message = "수량은 필수입니다.")
        @Positive(message = "수량은 양수여야 합니다.")
        @JsonDeserialize(using = IntegralLongDeserializer.class)
        Long quantity,

        @NotNull(message = "수량 단위는 필수입니다.")
        MaterialQuantityUnit unit,

        @NotNull(message = "결제 수단은 필수입니다.")
        PaymentMethod paymentMethod) {
}
