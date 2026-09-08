package com.ssafy.upcycle.product.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ssafy.upcycle.material.dto.IntegralLongDeserializer;
import com.ssafy.upcycle.product.entity.ProductCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 제품 모델 등록 요청.
 *
 * <p>요청이 지정할 수 없는 값이 둘 있다.
 *
 * <ul>
 *   <li>{@code brandId} — JWT 에서 꺼낸다. 바디로 받으면 남의 이름으로 제품을 올릴 수 있다.</li>
 *   <li>{@code status} — 등록은 언제나 {@code DRAFT} 다. 개체가 하나도 없는 제품이 마켓에
 *       뜨는 것을 막는다. 판매 시작은 별도 경로다.</li>
 * </ul>
 *
 * <p>{@code thumbnailUrl} 은 이미 업로드된 파일의 주소다. 업로드 자체는 파일 API 가 맡고
 * 등록은 결과만 받는다 — 소재 등록이 증빙을 다루는 방식과 같다.
 *
 * <p>금액 단위는 <b>원</b>이다. {@code price} 에 {@link IntegralLongDeserializer} 를 붙인 이유는
 * Jackson 이 기본적으로 {@code 129000.5} 를 {@code Long} 129000 으로 버리기 때문이다 —
 * 요청은 200 으로 성공하면서 등록된 가격만 조용히 달라진다. 거래 금액 필드가 같은 이유로
 * 같은 것을 쓴다.
 */
public record ProductModelRegisterRequest(

        @NotBlank(message = "제품명은 필수입니다.")
        @Size(max = 200, message = "제품명은 200자를 넘을 수 없습니다.")
        String name,

        @NotNull(message = "카테고리는 필수입니다.")
        ProductCategory category,

        @NotNull(message = "판매가는 필수입니다.")
        @PositiveOrZero(message = "판매가는 0원 이상이어야 합니다.")
        @JsonDeserialize(using = IntegralLongDeserializer.class)
        Long price,

        @NotBlank(message = "제품 설명은 필수입니다.")
        String description,

        @NotBlank(message = "대표 이미지는 필수입니다.")
        @Size(max = 500, message = "이미지 주소는 500자를 넘을 수 없습니다.")
        String thumbnailUrl) {
}
