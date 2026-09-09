package com.ssafy.upcycle.production.dto;

import com.ssafy.upcycle.production.entity.ProductionBatch;
import com.ssafy.upcycle.production.entity.ProductionBatchStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 배치와 그 투입 목록.
 *
 * <p>투입을 함께 담는 이유는 화면이 언제나 둘을 같이 보여주기 때문이다. 나눠 두면 두 번
 * 호출하게 되고, 그 사이에 투입이 바뀌면 <b>합계와 목록이 어긋난 화면</b>이 그려진다.
 *
 * <p>{@code upcycleRatio} 는 <b>확정 전에는 0</b> 이다. 계산은 확정 시점에 한 번 하고
 * 스냅샷으로 굳힌다 — 매번 재계산하면 소재 데이터가 바뀔 때 이미 팔린 제품의 비율이
 * 소급해 변한다.
 */
public record ProductionBatchResponse(
        Long productionBatchId,
        String batchCode,
        String batchName,
        Long productModelId,
        int producedQuantity,
        Long producerId,
        String producerWallet,
        ProductionBatchStatus status,
        long totalUsedQuantity,
        long virginUsedQuantity,
        long upcycledUsedQuantity,
        BigDecimal upcycleRatio,
        LocalDateTime createdAt,
        LocalDateTime confirmedAt,
        List<ProductionInputResponse> inputs) {

    public static ProductionBatchResponse of(
            ProductionBatch batch, List<ProductionInputResponse> inputs) {
        return new ProductionBatchResponse(
                batch.getId(),
                batch.getBatchCode(),
                batch.getBatchName(),
                batch.getProductModelId(),
                batch.getProducedQuantity(),
                batch.getProducerId(),
                batch.getProducerWallet(),
                batch.getStatus(),
                batch.getTotalUsedQuantity(),
                batch.getVirginUsedQuantity(),
                batch.getUpcycledUsedQuantity(),
                batch.getUpcycleRatio(),
                batch.getCreatedAt(),
                batch.getConfirmedAt(),
                inputs);
    }
}
