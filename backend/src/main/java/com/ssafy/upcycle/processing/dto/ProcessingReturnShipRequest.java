package com.ssafy.upcycle.processing.dto;
import jakarta.validation.constraints.*;
public record ProcessingReturnShipRequest(@Size(max=50) String carrier,
        @Size(max=100) String trackingNumber,
        @NotNull @Positive Long evidenceFileId,
        @NotBlank String txHash) {}
