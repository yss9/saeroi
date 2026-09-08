package com.ssafy.upcycle.processing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ProcessorCapabilitiesUpdateRequest(
        @NotEmpty(message = "소재·공정 조건을 한 개 이상 등록해 주세요.")
        List<@Valid ProcessorCapabilityRequest> capabilities) {
}
