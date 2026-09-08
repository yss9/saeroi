package com.ssafy.upcycle.processing.repository;

import com.ssafy.upcycle.processing.entity.ProcessorLeadTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessorLeadTimeRepository extends JpaRepository<ProcessorLeadTime, Long> {
    List<ProcessorLeadTime> findAllByProcessorProfileIdOrderByMaxQuantityAsc(Long processorId);
    void deleteAllByProcessorProfileId(Long processorId);
}
