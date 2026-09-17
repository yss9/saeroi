package com.ssafy.upcycle.prediction.repository;
import com.ssafy.upcycle.prediction.entity.PredictionBucket;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PredictionBucketRepository extends JpaRepository<PredictionBucket,Long> { List<PredictionBucket> findAllByMarketIdOrderByBucketIndex(Long marketId); }
