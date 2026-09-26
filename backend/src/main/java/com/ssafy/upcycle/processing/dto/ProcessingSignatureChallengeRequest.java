package com.ssafy.upcycle.processing.dto;

import com.ssafy.upcycle.processing.entity.ProcessingSignatureAction;
import jakarta.validation.constraints.*;

public record ProcessingSignatureChallengeRequest(
        @NotNull ProcessingSignatureAction action,
        @Size(max=500) String rejectionReason) {}
