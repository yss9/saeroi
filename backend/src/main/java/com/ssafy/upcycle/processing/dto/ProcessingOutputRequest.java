package com.ssafy.upcycle.processing.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ssafy.upcycle.material.entity.MaterialType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ProcessingOutputRequest(
        @NotNull MaterialType materialType,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 50) String grade,
        @NotNull @Positive @JsonDeserialize(using = GramQuantityDeserializer.class) Long quantity) {
}
