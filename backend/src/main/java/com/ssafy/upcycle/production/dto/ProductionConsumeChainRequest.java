package com.ssafy.upcycle.production.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 소재 소각의 온체인 결과를 서버에 알리는 요청. <b>온체인 3단계다.</b>
 *
 * <p>생산 확정은 소재를 DB 에서만 줄인다. 온체인 토큰은 그대로 남아 있어서, 체인만 보면
 * 브랜드가 여전히 그 소재를 갖고 있는 것처럼 보인다 — 같은 소재로 두 번 업사이클했다고
 * 주장할 수 있게 된다.
 *
 * <p><b>잠금 번호를 받지 않는다.</b> 그 값은 2단계({@code submitPlanAndLock}) 영수증에서
 * 나오고 {@code POST /{id}/chain} 이 이미 받아 두었다. 3단계까지 화면이 들고 오게 하면
 * 그 사이 새로고침 한 번에 잃어버린다.
 *
 * <p><b>체인 ID 와 컨트랙트 주소도 받지 않는다</b> — 서버 설정이 정본이다.
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

        Long blockNumber) {
}
