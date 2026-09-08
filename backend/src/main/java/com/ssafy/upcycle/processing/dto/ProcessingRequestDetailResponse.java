package com.ssafy.upcycle.processing.dto;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.processing.entity.*;
import java.time.LocalDateTime;
import java.util.List;
public record ProcessingRequestDetailResponse(Long requestId, String requestCode, Long brandId, String brandName,
        Long processorId, String processorName, Long materialId, String materialCode, String materialName,
        MaterialType materialType, long quantity, List<ProcessType> processTypes,
        ProcessingRequestStatus status, LocalDateTime createdAt, LocalDateTime expiresAt) {}
