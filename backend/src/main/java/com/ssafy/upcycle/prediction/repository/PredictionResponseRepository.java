package com.ssafy.upcycle.prediction.repository;
import com.ssafy.upcycle.prediction.entity.PredictionResponse;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PredictionResponseRepository extends JpaRepository<PredictionResponse,Long> {
    boolean existsByMarketIdAndUserId(Long marketId,Long userId);
    List<PredictionResponse> findAllByMarketIdOrderByCreatedAtAsc(Long marketId);
}
