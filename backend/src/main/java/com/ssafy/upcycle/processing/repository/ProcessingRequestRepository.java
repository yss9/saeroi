package com.ssafy.upcycle.processing.repository;

import com.ssafy.upcycle.processing.entity.*;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProcessingRequestRepository extends JpaRepository<ProcessingRequest, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ProcessingRequest r where r.id = :id")
    Optional<ProcessingRequest> findForUpdate(@Param("id") Long id);

    List<ProcessingRequest> findTop100ByStatusAndExpiresAtLessThanEqualOrderById(
            ProcessingRequestStatus status, LocalDateTime now);
}
