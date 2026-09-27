package com.ssafy.upcycle.prediction.dto;

import java.time.LocalDateTime;

/** 자유 의견의 익명 사본. 소비자·지갑·트랜잭션 식별자는 의도적으로 싣지 않는다. */
public record BrandPredictionCommentResponse(
        String reasonCode,
        String reasonText,
        LocalDateTime submittedAt) { }
