package com.ssafy.upcycle.processing.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @Entity @Table(name = "processing_input")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessingInput {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="processing_job_id", nullable=false) private Long processingJobId;
    @Column(name="material_id", nullable=false) private Long materialId;
    @Column(nullable=false) private long quantity;
    @Column(name="material_lock_id", nullable=false) private Long materialLockId;
    public ProcessingInput(Long jobId, Long materialId, long quantity, Long lockId) {
        if (jobId == null || materialId == null || quantity <= 0 || lockId == null) throw new IllegalArgumentException();
        this.processingJobId=jobId; this.materialId=materialId; this.quantity=quantity; this.materialLockId=lockId;
    }
}
