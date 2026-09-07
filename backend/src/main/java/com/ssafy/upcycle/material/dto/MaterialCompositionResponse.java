package com.ssafy.upcycle.material.dto;

import com.ssafy.upcycle.material.entity.MaterialComposition;
import java.math.BigDecimal;

/** 구성 한 줄의 응답. 정렬(비율 내림차순)은 리포지토리가 보장한다. */
public record MaterialCompositionResponse(String fiberType, BigDecimal percentage) {

    public static MaterialCompositionResponse from(MaterialComposition composition) {
        return new MaterialCompositionResponse(
                composition.getFiberType(), composition.getPercentage());
    }
}
