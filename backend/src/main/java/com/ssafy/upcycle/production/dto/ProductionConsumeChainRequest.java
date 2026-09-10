package com.ssafy.upcycle.production.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.List;

/**
 * 소재 소각의 온체인 결과를 서버에 알리는 요청.
 *
 * <p>생산 확정은 소재를 <b>DB 에서만</b> 줄인다. 온체인 토큰은 그대로 남아 있어서, 체인만
 * 보면 브랜드가 여전히 그 소재를 갖고 있는 것처럼 보인다 — 같은 소재로 두 번 업사이클했다고
 * 주장할 수 있게 된다. 화면이 {@code consumeLockedMaterial} 로 소각한 뒤 그 결과를 여기로
 * 알려 주면 두 쪽이 맞춰진다.
 *
 * <h2>온체인 잠금 번호를 여기서 받는다</h2>
 *
 * <p>{@code material_lock.chain_lock_id} 는 지금까지 아무도 채우지 않았다. DB 잠금과 온체인
 * 잠금이 따로 만들어지고 둘을 잇는 코드가 없었기 때문이다. 소각 함수가 온체인
 * {@code lockId} 를 인자로 받으므로, <b>소각을 보고하는 이 시점이 두 번호가 한자리에 모이는
 * 유일한 순간</b>이다.
 *
 * <p>잠금 생성 시점에 따로 연결 API 를 두지 않은 이유는 왕복이 하나 늘고, <b>잠금만 온체인에
 * 있고 소각은 안 된</b> 중간 상태가 생기기 때문이다.
 *
 * <p><b>체인 ID 와 컨트랙트 주소는 받지 않는다</b> — 서버 설정이 정본이다.
 *
 * <p>온체인 순서는 {@code createBatch} → {@code submitPlanAndLock} →
 * {@code confirmProduction} 이며, 이 요청은 마지막 단계의 결과를 알린다. 소각 자체는
 * {@code MaterialToken.consumeLockedMaterial} 이 하지만 그것은 레지스트리가 내부에서 부르는
 * 호출이라, 브랜드가 서명한 함수는 {@code confirmProduction} 이다.
 */
public record ProductionConsumeChainRequest(

        @NotBlank(message = "트랜잭션 해시는 필수입니다.")
        @Pattern(regexp = "^0x[0-9a-fA-F]{64}$", message = "트랜잭션 해시 형식이 올바르지 않습니다.")
        String txHash,

        Long blockNumber,

        @NotEmpty(message = "소각된 잠금을 하나 이상 지정해야 합니다.")
        @Valid
        List<ConsumedLock> consumedLocks) {

    /**
     * 투입 하나와 그것이 소각한 온체인 잠금.
     *
     * @param productionInputId DB 투입 id. 어느 잠금인지는 서버가 그 행에서 꺼낸다 —
     *     잠금 id 를 직접 받으면 남의 잠금을 이 배치의 것으로 보고할 수 있다
     * @param chainLockId 온체인 {@code MaterialLock} 번호.
     *     <b>이벤트에는 실리지 않는다</b> — {@code submitPlanAndLock} 이 잠금을 만들지만
     *     {@code ProductionPlanSubmitted} 에도, {@code MaterialConsumed} 에도 lockId 가 없다.
     *     {@code ProductionRegistry.getMaterialUses(batchId)} 를 읽어 꺼낸다
     */
    public record ConsumedLock(
            @NotNull(message = "투입 id는 필수입니다.")
            @Positive(message = "투입 id는 양수여야 합니다.")
            Long productionInputId,

            @NotNull(message = "온체인 잠금 번호는 필수입니다.")
            @Positive(message = "온체인 잠금 번호는 양수여야 합니다.")
            Long chainLockId) {
    }
}
