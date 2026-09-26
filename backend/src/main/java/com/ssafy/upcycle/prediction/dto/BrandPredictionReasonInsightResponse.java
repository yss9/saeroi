package com.ssafy.upcycle.prediction.dto;

/** 선호 이유 코드별 응답 수. 응답이 없는 코드도 0으로 포함해 화면의 축이 시장마다 바뀌지 않는다. */
public record BrandPredictionReasonInsightResponse(String reasonCode, long responseCount) { }
