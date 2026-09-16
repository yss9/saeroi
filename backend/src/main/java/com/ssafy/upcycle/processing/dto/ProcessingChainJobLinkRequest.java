package com.ssafy.upcycle.processing.dto;
import jakarta.validation.constraints.*;
public record ProcessingChainJobLinkRequest(@NotNull @Positive Long onChainId,
        @NotNull @Positive Long chainLockId, @NotBlank String txHash,
        @NotNull @PositiveOrZero Long blockNumber) {}
