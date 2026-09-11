package com.ssafy.upcycle.processing.repository;

import com.ssafy.upcycle.processing.entity.ProcessingOutput;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessingOutputRepository extends JpaRepository<ProcessingOutput, Long> {
    List<ProcessingOutput> findAllByProcessingJobIdOrderById(Long jobId);
    long deleteByProcessingJobId(Long jobId);

    @Query("""
            select (count(o) > 0)
            from ProcessingOutput o
            join ProcessingJob j on j.id = o.processingJobId
            where o.outputMaterialId = :materialId
              and j.status = com.ssafy.upcycle.processing.entity.ProcessingJobStatus.CONFIRMED
              and j.completedAt is not null
            """)
    boolean existsConfirmedOutputMaterial(@Param("materialId") Long materialId);
}
