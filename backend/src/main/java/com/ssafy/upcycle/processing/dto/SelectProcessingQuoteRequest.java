package com.ssafy.upcycle.processing.dto;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;
public record SelectProcessingQuoteRequest(@NotNull PaymentMethod paymentMethod) {}
