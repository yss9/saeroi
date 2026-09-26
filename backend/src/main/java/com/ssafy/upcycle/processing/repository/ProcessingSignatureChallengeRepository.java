package com.ssafy.upcycle.processing.repository;

import com.ssafy.upcycle.processing.entity.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProcessingSignatureChallengeRepository extends JpaRepository<ProcessingSignatureChallenge, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ProcessingSignatureChallenge c where c.id=:id")
    Optional<ProcessingSignatureChallenge> findForUpdate(@Param("id") Long id);
    long deleteByProcessingJobIdAndActorIdAndActionAndUsedAtIsNull(Long jobId, Long actorId,
            ProcessingSignatureAction action);
}
