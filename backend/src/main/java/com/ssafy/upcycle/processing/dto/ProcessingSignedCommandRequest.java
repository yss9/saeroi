package com.ssafy.upcycle.processing.dto;

import jakarta.validation.constraints.*;

public record ProcessingSignedCommandRequest(
        @NotNull @Positive Long challengeId,
        @NotBlank @Size(max=132) String signature) {}
