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
 * 낮은 단계로 보고되면 안 되기 때문이다.
 *
 * <h2>지금은 두 단만 판정한다</h2>
 *
 * <pre>
 *   PRODUCT_LINKED        production_input + 배치 CONFIRMED   엔티티 없음
 *   PROCESSING_VERIFIED   processing_job.brand_approved_at    테이블·컬럼 없음
 *   TRANSACTION_VERIFIED  material_trade.buyer_signed_at      판정함
 *   SOURCE_VERIFIED       material.signed_at                  컬럼 없음
 *   REGISTERED            그 외                                판정함
 * </pre>
 *
 * <p>그럼에도 <b>오늘 기준으로는 결과가 정확하다.</b> processing 도메인은 견적 요청까지만
 * 들어와 있어 승인 시각을 남길 {@code processing_job} 이 없고, {@code material.signed_at} 도
 * 없다. 어떤 소재도 위 두 단계에 도달할 수 없으므로 낮게 보고하는 일이 생기지 않는다.
 *
 * <p>각 도메인이 합류하면 자기 단을 <b>위에</b> 얹는다. 순서가 뒤바뀌면 가공을 거친 소재가
 * TRANSACTION_VERIFIED 로 보고된다.
 */
@Component
@RequiredArgsConstructor
public class MaterialVerificationStateResolver {

    private final TradeVerification tradeVerification;

    public MaterialVerificationState resolve(Long materialId) {
        if (tradeVerification.hasBuyerSignedTrade(materialId)) {
            return MaterialVerificationState.TRANSACTION_VERIFIED;
        }
        return MaterialVerificationState.REGISTERED;
    }
}
