package com.ssafy.upcycle.production.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ssafy.upcycle.material.dto.IntegralLongDeserializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 배치가 무엇을 만들지 정하는 요청. {@code PATCH /api/v1/production-batches/{batchId}}.
 *
 * <p>배치를 열 때는 이 값이 선택이지만 <b>여기서는 필수</b>다. 지정하러 부르는 경로에서
 * 값을 비워 보내는 것은 의도가 아니라 실수이고, "지정 해제" 라는 기능은 없다 — 배치는
 * 확정 전에 반드시 제품을 가져야 하므로 되돌릴 이유가 없다.
 *
 * <p>수량({@code producedQuantity})은 여기서 못 바꾼다. 그 값은 확정 시점에 만들어질
 * {@code product_item} 의 개수라, 투입을 담은 뒤에 바뀌면 이미 계산한 업사이클 비율이
 * 가리키는 대상이 달라진다.
 */
public record ProductionBatchProductModelRequest(

        @NotNull(message = "생산할 제품은 필수입니다.")
        @Positive(message = "제품 id는 양수여야 합니다.")
        @JsonDeserialize(using = IntegralLongDeserializer.class)
        Long productModelId) {
}
