package com.ssafy.upcycle.processing.repository;

import com.ssafy.upcycle.processing.entity.ProcessingRequestInput;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProcessingRequestInputRepository extends JpaRepository<ProcessingRequestInput, Long> {
    Optional<ProcessingRequestInput> findByProcessingRequestId(Long requestId);
}
