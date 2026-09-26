package com.ssafy.upcycle.processing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SubmitProcessingResultRequest(
        @NotEmpty @Size(max = 20) List<@Valid ProcessingOutputRequest> outputs,
        @Pattern(regexp = "^0x[0-9a-fA-F]{64}$") String evidenceHash,
        @Size(max = 500) String manifestUrl) {
}
