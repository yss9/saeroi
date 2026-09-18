package com.ssafy.upcycle.prediction.dto;

/** 관리자가 내부 ID를 외우지 않고 예측시장 대상 생산 배치를 고르기 위한 응답. */
public record PredictionMarketEligibleBatchResponse(
        Long productionBatchId,
        Long chainBatchId,
        String batchCode,
        String batchName,
        String brandName,
        String productName,
        int productionQuantity) {
}
