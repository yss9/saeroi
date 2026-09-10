package com.ssafy.upcycle.processing.entity;

import com.ssafy.upcycle.material.entity.MaterialType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "processor_lead_time")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessorLeadTime {
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
    @Column(name = "max_quantity", nullable = false)
    private long maxQuantity;
    @Column(name = "lead_days", nullable = false)
    private int leadDays;

    private ProcessorLeadTime(Long processorProfileId, MaterialType materialType, ProcessType processType,
            long maxQuantity, int leadDays) {
        this.processorProfileId = processorProfileId;
        this.materialType = materialType;
        this.processType = processType;
        this.maxQuantity = maxQuantity;
        this.leadDays = leadDays;
    }

    public static ProcessorLeadTime of(Long processorProfileId, MaterialType materialType, ProcessType processType,
            long maxQuantity, int leadDays) {
        return new ProcessorLeadTime(processorProfileId, materialType, processType, maxQuantity, leadDays);
    }
}
