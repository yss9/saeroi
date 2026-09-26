package com.ssafy.upcycle.processing.dto;

import jakarta.validation.constraints.*;

public record RejectProcessingResultRequest(
        @NotNull @Positive Long challengeId,
        @NotBlank @Size(max=132) String signature,
        @NotBlank @Size(max=500) String reason) {}
