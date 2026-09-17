package com.ssafy.upcycle.prediction.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public record PredictionMarketCreateRequest(
        @NotNull @Positive Long productionBatchId,
        @NotNull LocalDateTime bettingEndsAt,
        @NotNull LocalDateTime salesStartsAt,
        @NotNull LocalDateTime salesEndsAt,
        @NotEmpty @Size(min=2,max=20) List<@Positive Integer> bucketUpperBounds) { }
