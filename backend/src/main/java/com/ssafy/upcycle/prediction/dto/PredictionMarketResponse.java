package com.ssafy.upcycle.prediction.dto;

import com.ssafy.upcycle.prediction.entity.PredictionMarket;
import java.time.LocalDateTime;
import java.util.List;

public record PredictionMarketResponse(
        Long id,long chainMarketId,Long productionBatchId,Long productModelId,String productName,
        String productThumbnailUrl,long unitPrice,int productionQuantity,String brandWallet,
        LocalDateTime bettingEndsAt,LocalDateTime salesStartsAt,LocalDateTime salesEndsAt,
        String status,Integer soldQuantity,Integer winningBucketIndex,String resultHash,
        List<PredictionBucketResponse> buckets) {
    public static PredictionMarketResponse from(PredictionMarket m,List<PredictionBucketResponse> buckets){
        return new PredictionMarketResponse(m.getId(),m.getChainMarketId(),m.getProductionBatchId(),m.getProductModelId(),m.getProductName(),m.getProductThumbnailUrl(),m.getUnitPrice(),m.getProductionQuantity(),m.getBrandWallet(),m.getBettingEndsAt(),m.getSalesStartsAt(),m.getSalesEndsAt(),m.getStatus().name(),m.getSoldQuantity(),m.getWinningBucketIndex(),m.getResultHash(),buckets);
    }
}
