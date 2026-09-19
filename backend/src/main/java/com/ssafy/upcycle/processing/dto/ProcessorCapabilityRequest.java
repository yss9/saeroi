package com.ssafy.upcycle.processing.dto;

import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.processing.entity.ProcessType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 가공사가 "이 소재를 이 공정으로, 하루 이만큼, kg 당 이 값에" 처리한다고 등록하는 한 줄.
 *
 * <p>견적이 사라지면서 <b>가공사가 적는 것이 이 둘로 줄었다.</b> 수량 구간과 소요일 표는
 * 없앴다 — 소요일은 {@code ⌈수량 ÷ dailyCapacity⌉} 로 나오고, 받을 수 있는 수량의 상한은
 * 두지 않는다(많으면 오래 걸릴 뿐이다).
 *
 * <p>{@code pricePerKg} 만 0 을 허용한다. 무상 가공이나 시범 운영이 있을 수 있고, 0원이
 * 실수인지 의도인지는 서버가 판단할 수 없다. 하루 처리량 0 은 다르다 — 그건 소요일의
 * 분모라 0 이면 계산 자체가 성립하지 않는다.
 */
public record ProcessorCapabilityRequest(

        @NotNull(message = "소재 유형은 필수입니다.")
        MaterialType materialType,

        @NotNull(message = "가공 공정은 필수입니다.")
        ProcessType processType,

        @Positive(message = "하루 처리량은 양의 정수여야 합니다.")
        long dailyCapacity,

        @PositiveOrZero(message = "kg 당 단가는 0 이상의 정수여야 합니다.")
        long pricePerKg) {
}
