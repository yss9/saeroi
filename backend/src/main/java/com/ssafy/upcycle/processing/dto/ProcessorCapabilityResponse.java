package com.ssafy.upcycle.processing.dto;

import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.processing.entity.ProcessType;

/** 가공사가 등록해 둔 처리 조건 한 줄. 브랜드 검색 결과의 가격·소요일이 여기서 나온다. */
public record ProcessorCapabilityResponse(
        MaterialType materialType,
        ProcessType processType,
        long dailyCapacity,
        long pricePerKg) {
}
