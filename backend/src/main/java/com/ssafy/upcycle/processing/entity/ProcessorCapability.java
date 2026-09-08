package com.ssafy.upcycle.processing.entity;

import com.ssafy.upcycle.material.entity.MaterialType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "processor_capability")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessorCapability {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    @Column(name = "processor_profile_id", nullable = false)
    private Long processorProfileId;
    @Enumerated(EnumType.STRING)
    @Column(name = "material_type", nullable = false, length = 50)
    private MaterialType materialType;
    @Enumerated(EnumType.STRING)
    @Column(name = "process_type", nullable = false)
    private ProcessType processType;
    @Column(name = "min_quantity", nullable = false)
    private long minQuantity;
    @Column(name = "max_quantity", nullable = false)
    private long maxQuantity;

    private ProcessorCapability(Long processorProfileId, MaterialType materialType, ProcessType processType,
            long minQuantity, long maxQuantity) {
        this.processorProfileId = processorProfileId;
        this.materialType = materialType;
        this.processType = processType;
        this.minQuantity = minQuantity;
        this.maxQuantity = maxQuantity;
    }

    public static ProcessorCapability of(Long processorProfileId, MaterialType materialType, ProcessType processType,
            long minQuantity, long maxQuantity) {
        return new ProcessorCapability(processorProfileId, materialType, processType, minQuantity, maxQuantity);
    }

    public boolean accepts(long quantity) {
        return minQuantity <= quantity && quantity <= maxQuantity;
    }
}
