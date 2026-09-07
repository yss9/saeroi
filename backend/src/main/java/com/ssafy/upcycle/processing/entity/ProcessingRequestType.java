package com.ssafy.upcycle.processing.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "processing_request_type")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessingRequestType {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    @Column(name = "processing_request_id", nullable = false)
    private Long processingRequestId;
    @Enumerated(EnumType.STRING)
    @Column(name = "process_type", nullable = false)
    private ProcessType processType;
    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    public ProcessingRequestType(Long processingRequestId, ProcessType processType, int sequenceNo) {
        this.processingRequestId = processingRequestId;
        this.processType = processType;
        this.sequenceNo = sequenceNo;
    }
}
