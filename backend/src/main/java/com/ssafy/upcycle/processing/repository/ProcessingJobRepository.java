package com.ssafy.upcycle.processing.repository;

import com.ssafy.upcycle.processing.entity.*;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from ProcessingJob j where j.id=:id")
    Optional<ProcessingJob> findForUpdate(@Param("id") Long id);
    List<ProcessingJob> findTop100ByStatusAndPaymentExpiresAtLessThanEqualOrderById(
            ProcessingJobStatus status, LocalDateTime now);
    @Query("select j from ProcessingJob j where j.brandId=:actorId or j.processorId=:actorId order by j.createdAt desc, j.id desc")
    List<ProcessingJob> findAllForActor(@Param("actorId") Long actorId);
}
