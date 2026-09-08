package com.ssafy.upcycle.processing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ProcessorLeadTimesUpdateRequest(
        @NotEmpty(message = "소요일 구간을 한 개 이상 등록해 주세요.")
        List<@Valid ProcessorLeadTimeRequest> leadTimes) {
}
