package com.ssafy.upcycle.material.dto;

import com.ssafy.upcycle.material.entity.MaterialEvent;
import com.ssafy.upcycle.material.entity.MaterialEventReferenceType;
import com.ssafy.upcycle.material.entity.MaterialEventType;
import java.time.LocalDateTime;
import java.util.List;

public record MaterialEventResponse(
        Long eventId,
        MaterialEventType eventType,
        Long actorId,
        MaterialEventReferenceType referenceType,
        Long referenceId,
        Long relatedMaterialId,
        Long quantity,
        String detail,
        LocalDateTime occurredAt) {

    public static MaterialEventResponse from(MaterialEvent event) {
        return new MaterialEventResponse(
                event.getId(),
                event.getEventType(),
                event.getActorId(),
                event.getReferenceType(),
                event.getReferenceId(),
                event.getRelatedMaterialId(),
                event.getQuantity(),
                event.getDetail(),
                event.getOccurredAt());
    }

    public record Page(Long materialId, List<MaterialEventResponse> events, long total, int page, int size) {
    }
}
