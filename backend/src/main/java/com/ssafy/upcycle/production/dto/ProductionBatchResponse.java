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
 * <p>{@code chainBatchId} 가 {@code null} 이면 아직 {@code createBatch} 를 부르지 않은
 * 배치다. 온체인 4단계 중 어디까지 왔는지는 {@code chainBatchId} → {@code evidenceHash}
 * 순으로 채워지는 것을 보면 된다.
 *
 * <p>{@code evidenceHash} 가 {@code null} 이면 <b>아직 온체인에 기록되지 않은</b> 배치다.
 * 화면이 "온체인 기록" 버튼을 언제 보여줄지 이 값으로 정한다.
 *
 * <p>{@code upcycleRatio} 는 <b>확정 전에는 0</b> 이다. 계산은 확정 시점에 한 번 하고
 * 스냅샷으로 굳힌다 — 매번 재계산하면 소재 데이터가 바뀔 때 이미 팔린 제품의 비율이
 * 소급해 변한다.
 */
public record ProductionBatchResponse(
        Long productionBatchId,
        Long chainBatchId,
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
        String evidenceHash,
        String manifestUrl,
        LocalDateTime createdAt,
        LocalDateTime confirmedAt,
        List<ProductionInputResponse> inputs) {

    public static ProductionBatchResponse of(
            ProductionBatch batch, List<ProductionInputResponse> inputs) {
        return new ProductionBatchResponse(
                batch.getId(),
                batch.getChainBatchId(),
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
                batch.getEvidenceHash(),
                batch.getManifestUrl(),
                batch.getCreatedAt(),
                batch.getConfirmedAt(),
                inputs);
    }
}
