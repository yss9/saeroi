package com.ssafy.upcycle.product.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ssafy.upcycle.material.dto.IntegralLongDeserializer;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 제품 정보 수정 요청. <b>부분 수정이다 — 보낸 필드만 바뀐다.</b>
 *
 * <p>전 필드가 {@code null} 을 허용한다. 등록 요청과 달리 {@code @NotBlank} 를 붙이지 않은
 * 이유는 "안 보냄" 을 표현할 방법이 그것뿐이기 때문이다. 대신 <b>보낸 값이 비어 있으면
 * 거절한다</b> — 이름을 빈 문자열로 바꾸는 것은 실수이지 의도가 아니다.
 *
 * <p><b>바꿀 수 없는 것이 셋 있다.</b>
 *
 * <ul>
 *   <li>{@code category} — 바꾸면 이미 그 분류로 팔린 제품의 이력이 흔들린다</li>
 *   <li>{@code status} — 재고를 봐야 정해지는 값이라 {@code PATCH /{id}/status} 가 맡는다</li>
 *   <li>{@code brandId} — 제품의 주인은 바뀌지 않는다</li>
 * </ul>
 *
 * <p>{@code thumbnailUrl} 은 이미지 업로드가 돌려준 저장 키다. 등록과 같은 검사를 거친다 —
 * 저장소에 없는 키를 넣으면 이미지가 깨진 제품이 된다.
 */
public record ProductModelUpdateRequest(

        @Size(max = 200, message = "제품명은 200자를 넘을 수 없습니다.")
        String name,

        @PositiveOrZero(message = "판매가는 0원 이상이어야 합니다.")
        @JsonDeserialize(using = IntegralLongDeserializer.class)
        Long price,

        String description,

        @Size(max = 500, message = "이미지 주소는 500자를 넘을 수 없습니다.")
        String thumbnailUrl) {

    /** 아무 필드도 보내지 않은 요청인가. 바꿀 것이 없으면 성공했다고 답할 것도 없다. */
    public boolean isEmpty() {
        return name == null && price == null && description == null && thumbnailUrl == null;
    }

    /** 보낸 값이 공백뿐인가. "안 보냄"({@code null})과 다르다. */
    public boolean hasBlankText() {
        return isBlank(name) || isBlank(description) || isBlank(thumbnailUrl);
    }

    private static boolean isBlank(String value) {
        return value != null && value.isBlank();
    }
}
