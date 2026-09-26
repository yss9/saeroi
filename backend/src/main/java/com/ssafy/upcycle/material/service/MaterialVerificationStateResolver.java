package com.ssafy.upcycle.material.service;

import com.ssafy.upcycle.material.entity.MaterialVerificationState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 소재가 어디까지 검증되었는지 조회 시점에 계산한다.
 *
 * <p><b>저장하지 않는 이유.</b> 컬럼으로 두면 단계가 올라갈 때마다 UPDATE 하는 코드가
 * 필요하고, 한 군데라도 빠뜨리거나 트랜잭션이 중간에 실패하면 "가공은 승인됐는데 상태는
 * 그대로" 인 불일치가 남는다. 네 단계 모두 다른 기록의 시각 값에서 파생되므로 그때그때
 * 계산하는 편이 안전하다. 단계는 한 방향으로만 올라가 소급 변경 위험도 없다.
 *
 * <p><b>위에서부터 검사해 처음 참인 단계를 반환한다.</b> 더 높은 단계에 도달한 소재가
 * 낮은 단계로 보고되면 안 되기 때문이다. 단을 추가할 때 <b>순서를 지키는 것이 전부다</b> —
 * 새 단을 아래에 잘못 끼우면 그보다 높이 간 소재가 그 단으로 보고된다.
 *
 * <h2>판정 근거</h2>
 *
 * <pre>
 *   PRODUCT_LINKED        production_input + 배치 CONFIRMED     엔티티 없음
 *   PROCESSING_VERIFIED   processing_output + processing_job.brand_reviewed_at
 *   TRANSACTION_VERIFIED  material_trade.buyer_signed_at        판정함
 *   SOURCE_VERIFIED       blockchain_transaction verifySource    판정함
 *   REGISTERED            그 외                                  판정함
 * </pre>
 *
 * <p>Processing은 {@link ProcessingVerification} 창구로 승인된 산출물을 판정한다.
 * 남은 {@code PRODUCT_LINKED} 단은 production 조회 창구가 합류할 때 가장 위에 추가한다.
 *
 * <h2>왜 SOURCE_VERIFIED 만 온체인을 보는가</h2>
 *
 * <p>다른 단은 우리 API 를 반드시 거치는 흐름의 부산물이라 사실이 DB 에 남는다.
 * {@code verifySource} 는 SUPPLIER 가 출처를 서명하는 독립 행위여서 대응하는 DB 사실이
 * 없다. 자세한 근거는 {@code docs/superpowers/specs/2026-09-07-material-verification-state.md}.
 */
@Component
@RequiredArgsConstructor
public class MaterialVerificationStateResolver {

    private final ProcessingVerification processingVerification;
    private final TradeVerification tradeVerification;
    private final BlockchainVerification blockchainVerification;

    public MaterialVerificationState resolve(Long materialId) {
        if (processingVerification.hasConfirmedProcessingResult(materialId)) {
            return MaterialVerificationState.PROCESSING_VERIFIED;
        }
        if (tradeVerification.hasBuyerSignedTrade(materialId)) {
            return MaterialVerificationState.TRANSACTION_VERIFIED;
        }
        if (blockchainVerification.hasVerifiedSource(materialId)) {
            return MaterialVerificationState.SOURCE_VERIFIED;
        }
        return MaterialVerificationState.REGISTERED;
    }
}
