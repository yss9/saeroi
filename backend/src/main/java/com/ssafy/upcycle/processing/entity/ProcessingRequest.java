package com.ssafy.upcycle.processing.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "processing_request")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessingRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    @Column(name = "request_code", nullable = false, length = 30, unique = true)
    private String requestCode;
    @Column(name = "brand_id", nullable = false)
    private Long brandId;
    @Column(name = "processor_id", nullable = false)
    private Long processorId;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProcessingRequestStatus status;
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ProcessingRequest create(Long brandId, Long processorId, LocalDateTime now) {
        if (brandId == null || processorId == null || now == null || brandId.equals(processorId)) {
            throw new IllegalArgumentException("Distinct brand and processor and creation time are required");
        }
        ProcessingRequest request = new ProcessingRequest();
        request.brandId = brandId;
        request.processorId = processorId;
        request.requestCode = "PR" + UUID.randomUUID().toString().replace("-", "").substring(0, 28);
        request.status = ProcessingRequestStatus.QUOTING;
        request.createdAt = now;
        request.expiresAt = now.plusDays(7);
        return request;
    }

    public boolean isDue(LocalDateTime now) {
        return status == ProcessingRequestStatus.QUOTING && !expiresAt.isAfter(now);
    }

    public void terminate(ProcessingRequestStatus target) {
        if (target != ProcessingRequestStatus.CANCELED && target != ProcessingRequestStatus.REJECTED
                && target != ProcessingRequestStatus.EXPIRED) {
            throw new IllegalArgumentException("Not a terminal request status");
        }
        if (status != ProcessingRequestStatus.QUOTING) {
            throw new IllegalStateException("Only a quoting request may terminate");
        }
        status = target;
    }

    public void select() {
        if (status != ProcessingRequestStatus.QUOTING) throw new IllegalStateException("Only a quoting request may be selected");
        status = ProcessingRequestStatus.SELECTED;
    }
}
