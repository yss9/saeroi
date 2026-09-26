package com.ssafy.upcycle.processing.dto;
import jakarta.validation.constraints.NotBlank;
public record ProcessingReceiveConfirmRequest(@NotBlank String txHash) {}
