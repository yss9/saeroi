package com.ssafy.upcycle.trade.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ssafy.upcycle.material.dto.IntegralLongDeserializer;
import com.ssafy.upcycle.material.dto.MaterialQuantityUnit;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 구매 요청. <b>부분 구매를 전제로 한다</b> — 판매 글의 전량이 아니라 원하는 수량을 보낸다.
 *
 * <p>{@code totalPrice} 를 받지 않는 것이 핵심이다. 금액을 클라이언트가 정하면 1 원짜리
 * 요청을 만들 수 있다. 서버가 판매 글의 {@code pricePerKg} 로 다시 계산하고, 화면이 미리
 * 보여준 금액은 응답의 {@code totalPrice} 와 대조만 한다.
 *
 * <p>{@code buyerId} 도 받지 않는다. JWT {@code sub} 에서 얻는다.
 *
 * <p><b>배송지를 여기서 받는다.</b> 소재를 어디로 보낼지 정하지 않은 채 거래를 만들면
 * 판매자가 발송할 수 없다. 실제로 배송 행이 없는 거래는 발송·수령이 모두 404 로 막힌다.
 * 나중에 채우는 별도 API 를 두지 않고 구매 시점에 함께 받는 이유다.
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
        PaymentMethod paymentMethod,

        @NotBlank(message = "수취인 이름은 필수입니다.")
        @Size(max = 100, message = "수취인 이름은 100자를 넘을 수 없습니다.")
        String receiverName,

        @NotBlank(message = "수취인 연락처는 필수입니다.")
        @Size(max = 20, message = "수취인 연락처는 20자를 넘을 수 없습니다.")
        String receiverPhone,

        @NotBlank(message = "우편번호는 필수입니다.")
        @Size(max = 20, message = "우편번호는 20자를 넘을 수 없습니다.")
        String zipCode,

        @NotBlank(message = "주소는 필수입니다.")
        @Size(max = 255, message = "주소는 255자를 넘을 수 없습니다.")
        String address,

        @Size(max = 255, message = "상세 주소는 255자를 넘을 수 없습니다.")
        String addressDetail) {
}
