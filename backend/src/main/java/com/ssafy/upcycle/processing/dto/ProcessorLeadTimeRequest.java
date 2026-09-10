package com.ssafy.upcycle.processing.dto;

import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.processing.entity.ProcessType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProcessorLeadTimeRequest(
        @NotNull(message = "소재 유형은 필수입니다.") MaterialType materialType,
        @NotNull(message = "가공 공정은 필수입니다.") ProcessType processType,
        @Positive(message = "구간 최대 수량은 양의 정수여야 합니다.") long maxQuantity,
        @Positive(message = "소요일은 양의 정수여야 합니다.") int leadDays) {
}
