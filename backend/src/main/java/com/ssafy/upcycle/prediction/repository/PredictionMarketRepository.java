package com.ssafy.upcycle.prediction.repository;

import com.ssafy.upcycle.prediction.entity.PredictionMarket;
import com.ssafy.upcycle.prediction.entity.PredictionMarketStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PredictionMarketRepository extends JpaRepository<PredictionMarket,Long> {
    Optional<PredictionMarket> findByChainMarketId(long chainMarketId);
    boolean existsByProductionBatchId(Long productionBatchId);
    List<PredictionMarket> findAllByOrderByCreatedAtDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PredictionMarket> findAllByStatusAndSalesEndsAtBefore(PredictionMarketStatus status, LocalDateTime at);
}
