package com.ssafy.upcycle.processing.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "processing_request_input")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessingRequestInput {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    @Column(name = "processing_request_id", nullable = false)
    private Long processingRequestId;
    @Column(name = "material_id", nullable = false)
    private Long materialId;
    @Column(name = "quantity", nullable = false)
    private long quantity;

    public ProcessingRequestInput(Long processingRequestId, Long materialId, long quantity) {
        this.processingRequestId = processingRequestId;
        this.materialId = materialId;
        this.quantity = quantity;
    }
}
