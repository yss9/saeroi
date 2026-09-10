package com.ssafy.upcycle.processing.dto;

import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.processing.entity.ProcessType;

public record ProcessorLeadTimeResponse(MaterialType materialType, ProcessType processType,
        long maxQuantity, int leadDays) {
}
