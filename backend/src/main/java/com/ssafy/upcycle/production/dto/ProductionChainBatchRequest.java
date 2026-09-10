package com.ssafy.upcycle.production.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * {@code ProductionRegistry.createBatch} 결과를 알리는 요청. <b>온체인 4단계 중 첫 번째다.</b>
 *
 * <pre>
 *   createBatch          → 이 요청           온체인 batchId 채번
 *   submitPlanAndLock    → POST /{id}/chain          계획 해시 + 소재 잠금
 *   confirmProduction    → POST /{id}/chain/consume  소각 · 자투리 · 비율
 *   linkProductItems     → (선택) 개체 연결
 * </pre>
 *
 * <p>번호를 서버가 받아 두는 이유는 뒤의 세 단계가 <b>모두 그 번호를 인자로 받기</b>
 * 때문이다. 화면이 붙들고 있으면 새로고침 한 번에 잃어버리고, 그러면 이미 체인에 만들어진
 * 배치를 다시 가리킬 방법이 없다.
 *
 * @param chainBatchId {@code ProductionBatchCreated} 이벤트의 {@code batchId}
 */
public record ProductionChainBatchRequest(

        @NotNull(message = "온체인 배치 번호는 필수입니다.")
        @Positive(message = "온체인 배치 번호는 양수여야 합니다.")
        Long chainBatchId,

        @NotBlank(message = "트랜잭션 해시는 필수입니다.")
        @Pattern(regexp = "^0x[0-9a-fA-F]{64}$", message = "트랜잭션 해시 형식이 올바르지 않습니다.")
        String txHash,

        Long blockNumber) {
}
