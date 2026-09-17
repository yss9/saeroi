package com.ssafy.upcycle.prediction.dto;

import com.ssafy.upcycle.prediction.entity.PredictionReasonCode;
import jakarta.validation.constraints.*;

public record PredictionSurveyRequest(
        @Min(0) int bucketIndex,
        @NotBlank @Pattern(regexp="^[0-9]+$") @Size(max=78) String stakeAmount,
        @NotNull PredictionReasonCode reasonCode,
        @Size(max=500) String reasonText,
        @NotBlank @Pattern(regexp="^0x[0-9a-fA-F]{64}$") String transactionHash) { }
