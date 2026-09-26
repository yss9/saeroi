package com.ssafy.upcycle.material.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * DB 를 정본으로 하는 소재 발행. <b>온체인 mint 없이 보유 관계를 만든다.</b>
 *
 * <p>{@code MaterialToken} 은 ERC1155 이고 최초 발행은 원래 {@code mintMaterial} 이 맡는다.
 * 그런데 컨트랙트가 아직 배포되지 않았고 ({@code blockchain/deployments/} 가 비어 있다),
 * 그동안 <b>등록한 소재를 팔 수 없는 상태</b>가 계속된다 — 판매 등록이
 * {@code MaterialAvailability} 로 보유량을 확인하는데 그 행이 아예 만들어지지 않기 때문이다.
 *
 * <p>그래서 이 구현이 그 자리를 채운다. 임시방편이 아니라 <b>발행의 오프체인 구현</b>이며,
 * 배포 이후에도 남는다 — 로컬 개발과 테스트에서 소재 하나 만들 때마다 지갑을 열고 채굴을
 * 기다릴 수는 없다.
 *
 * <p>{@code app.material.on-chain} 이 {@code true} 가 되면 이 빈은 등록되지 않는다.
 * 프로파일이 아니라 <b>명시적 설정</b>으로 고르는 것은 의도적이다 ({@code PaymentGatewayConfig}
 * 와 같은 이유) — "운영인데 설정이 빠져 조용히 오프체인으로 돌았다" 가 가장 나쁘다.
 */
@Component
@ConditionalOnProperty(name = "app.material.on-chain", havingValue = "false", matchIfMissing = true)
@RequiredArgsConstructor
public class OffChainMaterialIssuer implements MaterialIssuer {

    private final MaterialBalanceInitializer balanceInitializer;

    /**
     * 등록자의 활성 지갑으로 보유 행을 만든다.
     *
     * <p><b>지갑이 없으면 발행하지 않는다.</b> {@code material_balance.wallet_address} 가
     * {@code NOT NULL} 이라 기술적으로도 값이 필요하지만, 그보다 <b>소재를 소유하려면 지갑이
     * 있어야 한다</b>는 규칙이 이미 서 있다 — 거래 생성도 양쪽 활성 지갑을 요구하고
     * ({@code TradeCommandService#create}), 없으면 거래를 만들지 않는다. 등록만 예외였다.
     *
     * <p>이 예외는 {@link MaterialRegistrationService} 의 트랜잭션을 롤백시킨다. 소재는
     * 등록되고 보유만 없는 상태가 남지 않는다.
     */
    @Override
    public void issue(Long materialId, Long ownerId, long quantity) {
        balanceInitializer.open(materialId, ownerId, quantity);
    }
}
