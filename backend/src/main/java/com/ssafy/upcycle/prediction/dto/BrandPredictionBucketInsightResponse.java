package com.ssafy.upcycle.prediction.dto;

/** 브랜드가 보는 예상 판매량 구간별 참여 집계. 포인트는 uint256 범위를 보존하려고 문자열로 보낸다. */
public record BrandPredictionBucketInsightResponse(
        int index,
        int lowerBound,
        int upperBound,
        long responseCount,
        String stakeAmount) { }
