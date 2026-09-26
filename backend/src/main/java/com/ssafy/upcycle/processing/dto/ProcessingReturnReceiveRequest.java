package com.ssafy.upcycle.processing.dto;
import jakarta.validation.constraints.NotBlank;
public record ProcessingReturnReceiveRequest(@NotBlank String txHash) {}
