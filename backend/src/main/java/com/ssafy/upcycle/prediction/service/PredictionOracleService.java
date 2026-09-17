package com.ssafy.upcycle.prediction.service;

import com.ssafy.upcycle.prediction.entity.PredictionBucket;
import com.ssafy.upcycle.prediction.entity.PredictionMarket;
import com.ssafy.upcycle.prediction.entity.PredictionMarketStatus;
import com.ssafy.upcycle.prediction.repository.PredictionBucketRepository;
import com.ssafy.upcycle.prediction.repository.PredictionMarketRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Hash;

@Service
@RequiredArgsConstructor
@Slf4j
public class PredictionOracleService {
    private final PredictionMarketRepository markets;
    private final PredictionBucketRepository buckets;
    private final PredictionSalesCounter salesCounter;
    private final PredictionChainGateway chain;
    private final Clock clock;

    @Transactional
    public int resolveDueMarkets() {
        List<PredictionMarket> due = markets.findAllByStatusAndSalesEndsAtBefore(
                PredictionMarketStatus.OPEN, LocalDateTime.now(clock));
        int resolved = 0;
        for (PredictionMarket market : due) {
            market.startResolving();
            try {
                PredictionSalesCounter.Snapshot sales = salesCounter.calculate(
                        market.getProductModelId(), market.getSalesStartsAt(), market.getSalesEndsAt());
                int sold = Math.min(market.getProductionQuantity(), sales.netSoldQuantity());
                int bucket = winningBucket(market.getId(), sold);
                String resultHash = Hash.sha3String(String.join("|",
                        Long.toString(market.getChainMarketId()), Long.toString(market.getProductModelId()),
                        market.getSalesStartsAt().toString(), market.getSalesEndsAt().toString(),
                        Integer.toString(sales.grossPaidQuantity()), Integer.toString(sales.canceledQuantity()),
                        Integer.toString(sold)));
                String txHash = chain.submitResult(market.getChainMarketId(), sold, resultHash);
                market.resolve(sold, bucket, resultHash, txHash, LocalDateTime.now(clock));
                resolved++;
            } catch (RuntimeException exception) {
                market.reopenAfterFailure();
                log.error("Prediction market resolution failed: marketId={}, chainMarketId={}",
                        market.getId(), market.getChainMarketId(), exception);
            }
        }
        return resolved;
    }

    private int winningBucket(Long marketId, int sold) {
        return buckets.findAllByMarketIdOrderByBucketIndex(marketId).stream()
                .filter(value -> sold >= value.getLowerBound() && sold <= value.getUpperBound())
                .map(PredictionBucket::getBucketIndex).findFirst()
                .orElseThrow(() -> new IllegalStateException("No prediction bucket for sold quantity"));
    }
}
