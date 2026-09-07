package com.ssafy.upcycle.trade.service;

import com.ssafy.upcycle.material.service.TradeVerification;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * material 이 선언한 {@link TradeVerification} 창구를 trade 쪽에서 구현한다.
 *
 * <p>판정 근거는 {@code buyer_signed_at} 하나다. 정책 09.1 의 확정 조건 다섯 가지가 모두
 * 충족되어야만 수령 확인이 통과하고 그때 이 시각이 남으므로, 조건들을 다시 확인할 필요가
 * 없다. 결제 전이거나 배송 전이면 수령 확인 자체가 거부되어 시각이 기록되지 않는다.
 */
@Service
@RequiredArgsConstructor
public class TradeVerificationService implements TradeVerification {

    private final MaterialTradeRepository materialTradeRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean hasBuyerSignedTrade(Long materialId) {
        if (materialId == null) {
            return false;
        }
        return materialTradeRepository.existsByMaterialIdAndBuyerSignedAtIsNotNull(materialId);
    }
}
