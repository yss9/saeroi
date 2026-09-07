package com.ssafy.upcycle.material.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 구성 한 줄. "면 60%".
 *
 * <p>{@code BigDecimal} 인 이유는 컬럼이 {@code DECIMAL(5,2)} 이고, {@code double} 로
 * 받으면 60.0 + 40.0 이 100.00000000000001 이 되어 <b>합계 검사가 정상 입력을 거절</b>한다.
 *
 * <p>{@code inclusive = false} 로 0 을 배제한다. 0% 성분은 정보가 아니라 잡음이고,
 * DB {@code CHECK (percentage > 0 ...)} 와 조건을 맞춰 500 대신 400 을 만든다.
 */
public record MaterialCompositionRequest(
        @NotBlank(message = "섬유 종류는 필수입니다.")
        String fiberType,

        @NotNull(message = "비율은 필수입니다.")
        @DecimalMin(value = "0", inclusive = false, message = "비율은 0보다 커야 합니다.")
        @DecimalMax(value = "100", message = "비율은 100을 넘을 수 없습니다.")
        BigDecimal percentage) {
}
