package com.ssafy.upcycle.material.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 온체인이 정본일 때의 발행. <b>등록 시점에는 아무것도 하지 않는다.</b>
 *
 * <p>소재를 진짜로 발행하는 것은 {@code MaterialToken.mintMaterial} 이고, 그것은 공급자
 * 지갑이 서명한다. 그 서명이 끝나기 전에 보유 행을 만들어 두면 <b>지갑 팝업에서 취소한
 * 소재가 보유 목록에 남는다</b> — 사용자는 "등록 안 했는데 왜 있지" 가 되고, 그 소재를
 * 팔거나 가공에 넣을 수도 있다. 체인에는 없는 수량이 거래되는 것이다.
 *
 * <p>그래서 보유 행은 {@link MaterialChainLinkService} 가 mint 확정을 받을 때 만든다.
 * 그때까지 소재는 {@code token_id} 가 {@code NULL} 이고 보유 행이 없는 상태이며, 그것이
 * "등록됐지만 아직 온체인에 없음" 의 정확한 표현이다.
 *
 * <p>{@link OffChainMaterialIssuer} 와 이 구현 중 하나만 뜬다 —
 * {@code app.material.on-chain} 이 고른다. 프로파일이 아니라 명시적 설정인 이유는
 * "운영인데 설정이 빠져 조용히 오프체인으로 돌았다" 가 가장 나쁘기 때문이다.
 */
@Component
@ConditionalOnProperty(name = "app.material.on-chain", havingValue = "true")
public class OnChainMaterialIssuer implements MaterialIssuer {

    @Override
    public void issue(Long materialId, Long ownerId, long quantity) {
        // 의도적으로 비어 있다. mint 가 확정될 때 MaterialChainLinkService 가 만든다.
    }
}
