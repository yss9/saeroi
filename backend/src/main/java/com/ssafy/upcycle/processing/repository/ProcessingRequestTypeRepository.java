package com.ssafy.upcycle.processing.repository;

import com.ssafy.upcycle.processing.entity.ProcessingRequestType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessingRequestTypeRepository extends JpaRepository<ProcessingRequestType, Long> {
    List<ProcessingRequestType> findAllByProcessingRequestIdOrderBySequenceNoAsc(
            Long processingRequestId);
}
