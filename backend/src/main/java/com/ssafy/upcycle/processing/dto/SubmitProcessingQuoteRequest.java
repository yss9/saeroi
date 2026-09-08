package com.ssafy.upcycle.processing.dto;
import jakarta.validation.constraints.*;
public record SubmitProcessingQuoteRequest(
        @NotNull @PositiveOrZero Long price,
        @NotNull @Positive Integer leadDays,
        @Size(max=2000) String memo) {}
