package com.ssafy.upcycle.processing.dto;

import jakarta.validation.constraints.Positive;

public record ProcessorLeadTimeRequest(
        @Positive(message = "구간 최대 수량은 양의 정수여야 합니다.") long maxQuantity,
        @Positive(message = "소요일은 양의 정수여야 합니다.") int leadDays) {
}
