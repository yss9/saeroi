package com.ssafy.upcycle.processing.dto;
import java.time.LocalDateTime;
public record ProcessingPaymentReadyResponse(Long jobId, String tid, String redirectUrl, long amount,
        boolean live, LocalDateTime paymentExpiresAt) {}
