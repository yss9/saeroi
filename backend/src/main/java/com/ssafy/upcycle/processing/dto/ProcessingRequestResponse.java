package com.ssafy.upcycle.processing.dto;

import com.ssafy.upcycle.processing.entity.*;
import java.time.LocalDateTime;
import java.util.List;

public record ProcessingRequestResponse(
        Long requestId, String requestCode, ProcessingRequestStatus status,
        Long materialId, Long processorId, long quantity, List<ProcessType> processTypes,
        LocalDateTime createdAt, LocalDateTime expiresAt) {
    public static ProcessingRequestResponse of(ProcessingRequest request, CreateProcessingRequest input) {
        return new ProcessingRequestResponse(request.getId(), request.getRequestCode(), request.getStatus(),
                input.materialId(), request.getProcessorId(), input.quantity(), List.copyOf(input.processTypes()),
                request.getCreatedAt(), request.getExpiresAt());
    }
}
