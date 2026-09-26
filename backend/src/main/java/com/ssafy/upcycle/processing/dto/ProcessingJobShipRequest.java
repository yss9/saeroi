package com.ssafy.upcycle.processing.dto;

import jakarta.validation.constraints.Size;

public record ProcessingJobShipRequest(
        @Size(max=50) String carrier,
        @Size(max=100) String trackingNumber) {}
