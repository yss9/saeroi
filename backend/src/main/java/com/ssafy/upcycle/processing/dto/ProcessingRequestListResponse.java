package com.ssafy.upcycle.processing.dto;

import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.processing.entity.ProcessType;
import com.ssafy.upcycle.processing.entity.ProcessingRequestStatus;
import java.time.LocalDateTime;
import java.util.List;

public record ProcessingRequestListResponse(List<Item> items, long total, int page, int size) {
    public record Item(
            Long requestId,
            String requestCode,
            Long brandId,
            String brandName,
            Long processorId,
            String processorName,
            Long materialId,
            String materialCode,
            String materialName,
            MaterialType materialType,
            long quantity,
            List<ProcessType> processTypes,
            ProcessingRequestStatus status,
            LocalDateTime createdAt,
            LocalDateTime expiresAt) {}
}
