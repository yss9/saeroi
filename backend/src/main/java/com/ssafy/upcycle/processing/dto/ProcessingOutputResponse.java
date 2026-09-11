package com.ssafy.upcycle.processing.dto;

import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.processing.entity.ProcessingOutput;

public record ProcessingOutputResponse(Long outputId, Long materialId, MaterialType materialType,
        String grade, String name, long quantity) {
    public static ProcessingOutputResponse of(ProcessingOutput output) {
        return new ProcessingOutputResponse(output.getId(), output.getOutputMaterialId(),
                output.getMaterialType(), output.getGrade(), output.getName(), output.getQuantity());
    }
}
