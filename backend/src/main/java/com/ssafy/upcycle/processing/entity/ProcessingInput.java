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
    @Column(name="brand_material_lock_id", nullable=false) private Long brandMaterialLockId;
    @Column(name="processor_material_lock_id") private Long processorMaterialLockId;
    public ProcessingInput(Long jobId, Long materialId, long quantity, Long lockId) {
        if (jobId == null || materialId == null || quantity <= 0 || lockId == null) throw new IllegalArgumentException();
        this.processingJobId=jobId; this.materialId=materialId; this.quantity=quantity; this.brandMaterialLockId=lockId;
    }

    public void linkProcessorLock(Long lockId) {
        if (lockId == null || lockId <= 0) throw new IllegalArgumentException("Replacement lock is required");
        if (processorMaterialLockId != null && !processorMaterialLockId.equals(lockId)) {
            throw new IllegalStateException("Processor lock is already linked");
        }
        this.processorMaterialLockId = lockId;
    }

    /** 이전 처리 경로는 현재 활성 잠금 하나를 요구하므로 Processor 인수 전후를 모두 지원한다. */
    public Long getMaterialLockId() {
        return processorMaterialLockId == null ? brandMaterialLockId : processorMaterialLockId;
    }
}
