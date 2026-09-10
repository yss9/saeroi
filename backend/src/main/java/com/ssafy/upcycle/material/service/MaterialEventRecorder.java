package com.ssafy.upcycle.material.service;

import com.ssafy.upcycle.material.entity.MaterialEvent;
import com.ssafy.upcycle.material.entity.MaterialEventReferenceType;
import com.ssafy.upcycle.material.entity.MaterialEventType;
import com.ssafy.upcycle.material.repository.MaterialEventRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 업무 변경과 같은 트랜잭션에 Material 사건 한 건을 추가한다. */
@Service
@RequiredArgsConstructor
public class MaterialEventRecorder {

    private final MaterialEventRepository repository;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public MaterialEvent record(
            Long materialId,
            MaterialEventType eventType,
            Long actorId,
            MaterialEventReferenceType referenceType,
            Long referenceId,
            Long relatedMaterialId,
            Long quantity,
            String detail,
            String idempotencyKey) {
        var existing = repository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        return repository.save(MaterialEvent.record(
                materialId,
                eventType,
                actorId,
                referenceType,
                referenceId,
                relatedMaterialId,
                quantity,
                detail,
                idempotencyKey,
                LocalDateTime.now(clock)));
    }

    public static String key(MaterialEventType eventType, Long materialId, Long referenceId) {
        return eventType.name() + ":" + materialId + ":" + referenceId;
    }

    public static String key(
            MaterialEventType eventType, Long materialId, Long referenceId, Long discriminator) {
        return key(eventType, materialId, referenceId) + ":" + discriminator;
    }
}
