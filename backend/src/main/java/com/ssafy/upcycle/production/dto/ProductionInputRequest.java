package com.ssafy.upcycle.production.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ssafy.upcycle.material.dto.IntegralLongDeserializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 배치에 소재를 연결하는 요청. <b>제품과 소재를 잇는 지점이다.</b>
 *
 * <p>투입량을 셋으로 나눠 받는다.
 *
 * <pre>
 *   inputQuantity = productUsedQuantity     제품에 실제로 들어간 양
 *                 + reusableScrapQuantity   자투리 (새 소재로 되살아난다)
 *                 + wasteQuantity           폐기
 * </pre>
 *
 * <p><b>합계만 받고 서버가 쪼개는 방식은 불가능하다.</b> 어디까지가 제품이 되고 어디부터
 * 자투리인지는 실제로 재단해 본 브랜드만 안다. 대신 <b>식이 맞는지는 서버가 검사한다</b> —
 * ERD 가 "행마다 검증한다" 고 못 박았고, 배치 합계로만 보면 한 소재를 과다 입력하고 다른
 * 소재를 과소 입력해도 통과한다.
 *
 * <p>단위는 <b>gram</b>이다. 소재 잔량이 gram 으로 저장되므로 여기서 단위를 바꾸면 두 값이
 * 어긋난다.
 *
 * <p>{@code materialLockId} 를 받지 않는다. 잠금은 서버가 이 요청을 처리하면서 직접 건다 —
 * 클라이언트가 잠금 id 를 고르게 하면 남이 잡아 둔 잠금을 자기 투입에 붙일 수 있다.
 */
public record ProductionInputRequest(

        @NotNull(message = "투입할 소재는 필수입니다.")
        @Positive(message = "소재 id는 양수여야 합니다.")
        @JsonDeserialize(using = IntegralLongDeserializer.class)
        Long materialId,

        @NotNull(message = "투입량은 필수입니다.")
        @Positive(message = "투입량은 1g 이상이어야 합니다.")
        @JsonDeserialize(using = IntegralLongDeserializer.class)
        Long inputQuantity,

        @NotNull(message = "제품 사용량은 필수입니다.")
        @PositiveOrZero(message = "제품 사용량은 0 이상이어야 합니다.")
        @JsonDeserialize(using = IntegralLongDeserializer.class)
        Long productUsedQuantity,

        @NotNull(message = "재사용 자투리는 필수입니다.")
        @PositiveOrZero(message = "재사용 자투리는 0 이상이어야 합니다.")
        @JsonDeserialize(using = IntegralLongDeserializer.class)
        Long reusableScrapQuantity,

        @NotNull(message = "폐기량은 필수입니다.")
        @PositiveOrZero(message = "폐기량은 0 이상이어야 합니다.")
        @JsonDeserialize(using = IntegralLongDeserializer.class)
        Long wasteQuantity,

        @Size(max = 200, message = "폐기 사유는 200자를 넘을 수 없습니다.")
        String wasteReason) {
}
