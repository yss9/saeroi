package com.ssafy.upcycle.processing.repository;

import com.ssafy.upcycle.processing.entity.ProcessingQuote;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProcessingQuoteRepository extends JpaRepository<ProcessingQuote, Long> {
    boolean existsByProcessingRequestIdAndProcessorId(Long requestId, Long processorId);
    List<ProcessingQuote> findAllByProcessingRequestIdOrderByCreatedAtAscIdAsc(Long requestId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from ProcessingQuote q where q.id=:id")
    Optional<ProcessingQuote> findForUpdate(@Param("id") Long id);
}
