package com.ssafy.upcycle.processing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProcessorProfileRequest(
        @NotBlank(message = "지역을 입력해 주세요.")
        @Size(max = 100, message = "지역은 100자 이하여야 합니다.")
        String region) {
}
