package com.ssafy.upcycle.processing.entity;

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
    @Column(name = "max_quantity", nullable = false)
    private long maxQuantity;
    @Column(name = "lead_days", nullable = false)
    private int leadDays;
}
