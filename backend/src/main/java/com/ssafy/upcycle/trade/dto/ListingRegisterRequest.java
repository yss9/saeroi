package com.ssafy.upcycle.trade.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ssafy.upcycle.material.dto.IntegralLongDeserializer;
import com.ssafy.upcycle.material.dto.MaterialQuantityUnit;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 판매 등록 요청.
 *
 * <p>{@code sellerId} 를 받지 않는다. JWT {@code sub} 에서 얻는다 — 바디로 받으면 남의
 * 계정 이름으로 남의 소재를 팔 수 있다.
 *
 * <p><b>수량은 {@code quantity} + {@code unit} 두 필드다</b> ({@code MaterialRegisterRequest}
 * 와 같은 규칙). 저장은 gram 정수이고 화면의 kg 표기는 표현 계층 책임이다.
 * {@link IntegralLongDeserializer} 를 붙인 이유도 같다 — Jackson 이 {@code 12.5} 를
 * {@code Long} 12 로 조용히 버리면 500g 이 요청 성공과 함께 사라진다.
 *
 * <p><b>가격 단위는 kg 당 원이다.</b> 수량과 단위가 다른 것이 의도적이다 — 소재 시세는
 * kg 단가로 이야기하고, gram 단가로 받으면 화면이 매번 나눠 보여야 한다. 총액 계산은
 * {@code TradePricing} 한 곳에서만 한다.
 *
 * <p>0 원을 허용한다 ({@code @PositiveOrZero}). 스키마의 {@code chk_material_listing_price}
 * 도 {@code >= 0} 이고, 무상 양도가 거래 흐름을 그대로 타는 편이 별도 경로를 만드는 것보다 낫다.
 */
public record ListingRegisterRequest(

        @NotNull(message = "소재는 필수입니다.")
        Long materialId,

        @NotNull(message = "수량은 필수입니다.")
        @Positive(message = "수량은 양수여야 합니다.")
        @JsonDeserialize(using = IntegralLongDeserializer.class)
        Long quantity,

        @NotNull(message = "수량 단위는 필수입니다.")
        MaterialQuantityUnit unit,

        @NotNull(message = "kg 당 가격은 필수입니다.")
        @PositiveOrZero(message = "가격은 0 이상이어야 합니다.")
        @JsonDeserialize(using = IntegralLongDeserializer.class)
        Long pricePerKg) {
}
