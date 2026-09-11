package com.ssafy.upcycle.processing.dto;

import com.ssafy.upcycle.material.entity.MaterialType;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.*;

public record ProcessingOutputRequest(
        @NotNull MaterialType materialType,
        @NotBlank @Size(max=50) String grade,
        @NotBlank @Size(max=200) String name,
        @NotNull @Positive @JsonDeserialize(using = GramQuantityDeserializer.class) Long quantity) {}
