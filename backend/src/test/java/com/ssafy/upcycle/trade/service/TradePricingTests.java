package com.ssafy.upcycle.trade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.upcycle.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

/**
 * 거래 금액 계산.
 *
 * <p>여기서 고정하는 것은 <b>버림</b>과 <b>단위</b> 두 가지다. 프론트의
 * {@code calculateTotalPrice} 가 같은 규칙을 쓰며, 두 곳이 어긋나면 화면이 보여준 금액과
 * 서버가 대조하는 금액이 달라 결제가 통째로 막힌다.
 */
class TradePricingTests {

    @Test
    void multipliesGramsByKilogramPrice() {
        // 3kg × 14,000원/kg = 42,000원. 화면 테스트(PurchaseForm)가 쓰는 것과 같은 예다.
        assertThat(TradePricing.totalPrice(3_000L, 14_000L)).isEqualTo(42_000L);
    }

    @Test
    void handlesQuantitiesThatAreNotWholeKilograms() {
        // 2.3kg = 2,300g, × 4,200원/kg = 9,660원.
        assertThat(TradePricing.totalPrice(2_300L, 4_200L)).isEqualTo(9_660L);
    }

    @Test
    void roundsDownRatherThanUp() {
        // 1g × 1,001원/kg = 1.001원. 반올림으로 바꾸면 여기가 1 이 아니라 다른 값이 된다.
        assertThat(TradePricing.totalPrice(1L, 1_001L)).isEqualTo(1L);
    }

    @Test
    void roundsDownToZeroForNegligibleAmounts() {
        // 1g × 999원/kg = 0.999원 → 0원. 0원 거래가 만들어질 수 있다는 뜻이며,
        // 스키마도 total_price >= 0 을 허용한다. 최소 금액 정책이 생기면 여기가 아니라
        // 거래 생성 쪽에서 막아야 한다 — 계산은 계산일 뿐이다.
        assertThat(TradePricing.totalPrice(1L, 999L)).isZero();
    }

    @Test
    void treatsAFreeListingAsZero() {
        assertThat(TradePricing.totalPrice(30_000L, 0L)).isZero();
    }

    @Test
    void rejectsAnOverflowingMultiplication() {
        // 조용히 음수로 감기면 chk_material_trade_values 위반으로 설명할 수 없는 500 이 된다.
        assertThatThrownBy(() -> TradePricing.totalPrice(Long.MAX_VALUE, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("계산");
    }
}
