package com.ssafy.upcycle.processing.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ssafy.upcycle.processing.entity.ProcessType;
import jakarta.validation.constraints.*;
import java.util.List;

public record CreateProcessingRequest(
        @NotNull @Positive Long materialId,
        @NotNull @Positive Long processorId,
        @NotNull @Positive @JsonDeserialize(using = GramQuantityDeserializer.class) Long quantity,
        @NotEmpty @Size(max = 8) List<@NotNull ProcessType> processTypes) {
}
