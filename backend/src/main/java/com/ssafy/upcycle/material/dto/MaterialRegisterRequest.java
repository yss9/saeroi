package com.ssafy.upcycle.material.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ssafy.upcycle.material.entity.MaterialOriginReason;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 소재 등록 요청. <b>서명 전</b>이라 온체인 값은 받지 않는다.
 *
 * <p>{@code generationType} 이 없는 것이 의도적이다. 등록은 항상 {@code INITIAL} 이고
 * {@code PROCESSED}·{@code PRODUCTION_SCRAP} 은 가공·생산 확정이 만들어내는 값이다.
 * 요청이 지정할 수 있게 하면 <b>가공을 거치지 않은 소재를 "가공됨" 으로 올릴 수 있다</b> —
 * 신뢰 모델의 근거가 바로 그 지점이다. {@code Material.register} 가 값을 고정한다.
 *
 * <p>{@code registrantId} 도 받지 않는다. JWT {@code sub} 에서 얻는다 — 바디로 받으면
 * 남의 계정 이름으로 등록할 수 있다.
 *
 * <p><b>수량은 {@code quantity} + {@code unit} 두 필드다.</b> 저장·응답은 gram 정수
 * ({@code totalQuantity})이므로 이름이 다르다.
 *
 * <p>{@code quantity} 에 {@link IntegralLongDeserializer} 를 붙인 것이 핵심이다.
 * Jackson 은 기본 설정에서 {@code 50.5} 를 {@code Long} 50 으로 <b>조용히 버린다</b> —
 * 소재 수량에서 그 0.5 는 500g 이고, 요청은 200 으로 성공하면서 등록된 값만 달라진다.
 * 기본 동작에 맡기면 검증을 통과해 버리므로 역직렬화 단계에서 막는다.
 */
public record MaterialRegisterRequest(
        @NotNull(message = "소재 종류는 필수입니다.")
        MaterialType materialType,

        @NotNull(message = "소재 출처는 필수입니다.")
        MaterialRootOrigin rootOrigin,

        @NotBlank(message = "소재 이름은 필수입니다.")
        @Size(max = 200, message = "소재 이름은 200자를 넘을 수 없습니다.")
        String name,

        // 컬럼이 TEXT(65535 **바이트**)라 상한이 없으면 한글 21,846자쯤에서 DB 가 거절한다 —
        // 사용자에게는 400 이 아니라 500 으로 보인다. 바이트가 아니라 글자로 세는 @Size 를
        // 넉넉하되 안전한 값(5000자 × 최대 4바이트 = 20,000바이트)으로 둔다.
        @Size(max = 5000, message = "소재 설명은 5000자를 넘을 수 없습니다.")
        String description,

        @Size(max = 50, message = "등급은 50자를 넘을 수 없습니다.")
        String grade,

        @NotNull(message = "수량은 필수입니다.")
        @Positive(message = "수량은 양수여야 합니다.")
        @JsonDeserialize(using = IntegralLongDeserializer.class)
        Long quantity,

        @NotNull(message = "수량 단위는 필수입니다.")
        MaterialQuantityUnit unit,

        @Positive(message = "길이는 양수여야 합니다.")
        Long lengthMm,

        @Positive(message = "너비는 양수여야 합니다.")
        Long widthMm,

        /**
         * 왜 폐기물이 되었나. {@code rootOrigin=WASTE} 이면 필수다.
         *
         * <p>짝 검사를 Bean Validation 으로 하지 않는다 — 두 필드에 걸친 조건이라
         * 필드 애노테이션으로 표현되지 않고, 클래스 수준 제약을 만들면 규칙이 엔티티와
         * DTO 두 곳에 생긴다. {@code Material.register} 가 유일한 문이다.
         */
        MaterialOriginReason originReason,

        @Size(max = 200, message = "발생 사유 설명은 200자를 넘을 수 없습니다.")
        String originReasonNote,

        /**
         * 구성 성분. <b>선택</b>이다 — 모르는 소재를 등록하지 못하게 만들면 사용자가
         * 아무 값이나 채워 넣고, 그건 빈칸보다 나쁘다.
         *
         * <p>{@code @Valid} 가 있어야 목록 안쪽 제약이 검사된다. 없으면 비율 100 초과가
         * 그대로 통과해 DB {@code CHECK} 위반 500 이 된다.
         */
        @Valid
        List<MaterialCompositionRequest> composition) {
}
