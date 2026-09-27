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
    /** 시장 번호로 여는 제품 사진. 쇼핑몰 공개 썸네일은 판매 중인 제품만 열어 주어 쓸 수 없다. */
    private static final String THUMBNAIL_PATH = "/api/v1/prediction-markets/%d/thumbnail";

    public static PredictionMarketResponse from(PredictionMarket m,List<PredictionBucketResponse> buckets){
        return new PredictionMarketResponse(m.getId(),m.getChainMarketId(),m.getProductionBatchId(),m.getProductModelId(),m.getProductName(),thumbnailUrl(m),m.getUnitPrice(),m.getProductionQuantity(),m.getBrandWallet(),m.getBettingEndsAt(),m.getSalesStartsAt(),m.getSalesEndsAt(),m.getStatus().name(),m.getSoldQuantity(),m.getWinningBucketIndex(),m.getResultHash(),buckets);
    }

    /**
     * 화면이 그대로 그릴 수 있는 사진 주소.
     *
     * <p>시장은 사진을 <b>저장 키</b>로 굳혀 둔다. 키를 내려주면 {@code <img src="products/...">} 가
     * 되어 사진이 깨진다. 데모 상품은 프론트에 포함된 정적 이미지라 그대로 둔다.
     */
    private static String thumbnailUrl(PredictionMarket m) {
        String stored = m.getProductThumbnailUrl();
        if (stored == null || stored.isBlank()) return null;
        if (stored.startsWith("/images/landing/")) return stored;
        return THUMBNAIL_PATH.formatted(m.getId());
    }
}
