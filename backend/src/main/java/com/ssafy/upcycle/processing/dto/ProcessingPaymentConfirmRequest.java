package com.ssafy.upcycle.processing.dto;
import jakarta.validation.constraints.*;
public record ProcessingPaymentConfirmRequest(@NotBlank @Size(max=200) String pgToken) {}
