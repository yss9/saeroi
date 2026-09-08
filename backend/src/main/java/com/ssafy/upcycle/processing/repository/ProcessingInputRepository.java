package com.ssafy.upcycle.processing.repository;
import com.ssafy.upcycle.processing.entity.ProcessingInput;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProcessingInputRepository extends JpaRepository<ProcessingInput, Long> {
    Optional<ProcessingInput> findByProcessingJobId(Long jobId);
}
