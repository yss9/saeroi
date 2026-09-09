package com.ssafy.upcycle.production.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ssafy.upcycle.material.dto.IntegralLongDeserializer;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 생산 배치 열기 요청.
 *
 * <p>요청이 지정할 수 없는 값이 셋 있다.
 *
 * <ul>
 *   <li>{@code producerId} — JWT 에서 꺼낸다. 바디로 받으면 남의 이름으로 생산할 수 있다.</li>
 *   <li>{@code producerWallet} — 등록된 활성 지갑에서 가져온다. 바디로 받으면 남의 주소를
 *       서명자로 박아 넣을 수 있고, 그 주소는 온체인 기록에 그대로 남는다.</li>
 *   <li>{@code status} — 언제나 {@code DRAFT} 다. 투입을 담기 전에 확정될 수 없다.</li>
 * </ul>
 *
 * <p>수량 세 컬럼과 업사이클 비율도 받지 않는다. 확정 시점에 투입을 합쳐 계산한다 —
 * 요청이 정할 수 있으면 실제 투입과 다른 비율을 적어 넣을 수 있고, 그 값이 그대로
 * 제품에 붙는다.
 */
public record ProductionBatchOpenRequest(

        @NotBlank(message = "배치 이름은 필수입니다.")
        @Size(max = 200, message = "배치 이름은 200자를 넘을 수 없습니다.")
        String batchName,

        @NotNull(message = "생산할 제품은 필수입니다.")
        @Positive(message = "제품 id는 양수여야 합니다.")
        @JsonDeserialize(using = IntegralLongDeserializer.class)
        Long productModelId,

        @NotNull(message = "생산 개수는 필수입니다.")
        @Positive(message = "생산 개수는 1개 이상이어야 합니다.")
        Integer producedQuantity) {
}
