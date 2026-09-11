package com.ssafy.upcycle.processing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record ProcessingResultRequest(
        @PositiveOrZero long lossQuantity,
        @NotEmpty @Valid List<ProcessingOutputRequest> outputs) {}
