package com.ssafy.upcycle.processing.dto;

import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.processing.entity.ProcessType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProcessorCapabilityRequest(
        @NotNull MaterialType materialType,
        @NotNull ProcessType processType,
        @Positive(message = "최소 수량은 양의 정수여야 합니다.") long minQuantity,
        @Positive(message = "최대 수량은 양의 정수여야 합니다.") long maxQuantity) {
}
