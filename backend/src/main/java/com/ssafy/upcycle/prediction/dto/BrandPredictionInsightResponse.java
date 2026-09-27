package com.ssafy.upcycle.prediction.dto;

import java.util.List;

/** 브랜드가 자기 상품의 예측 결과와 선호 이유를 한 번에 보는 응답. */
public record BrandPredictionInsightResponse(
        PredictionMarketResponse market,
        long participantCount,
        String totalStakeAmount,
        List<BrandPredictionBucketInsightResponse> buckets,
        List<BrandPredictionReasonInsightResponse> reasons,
        List<BrandPredictionCommentResponse> comments) { }
