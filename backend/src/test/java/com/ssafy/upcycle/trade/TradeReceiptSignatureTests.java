package com.ssafy.upcycle.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * 수령 확인과 지갑 서명은 <b>다른 일</b>이다.
 *
 * <p>예전에는 수령 확인이 {@code buyer_signed_at} 까지 같은 값으로 채웠다. 그러면 지갑
 * 서명을 한 번도 하지 않은 거래가 "검증됨" 으로 남는다 — 그 값은 거래 검증의 유일한
 * 근거이고 판매 글의 검증 표시도 그것을 본다.
 *
 * <p>실제로 이렇게 새어 나갔다. 서버가 DB 의 이 값을 보고 "서명이 끝났다" 고 판단해 수령
 * 서명 카드를 띄우지 않았고, 체인에서는 소재가 판매자 잠금에 그대로 남았다. 나중에 그
 * 소재를 가공에 보내려 할 때 컨트랙트가 insufficient material 로 거절해서야 드러났다.
 */
class TradeReceiptSignatureTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 21, 12, 0);

    @Test
    void 수령_확인만으로는_서명_시각이_채워지지_않는다() {
        MaterialTrade trade = shipped();

        trade.confirmReceipt(NOW);

        assertThat(trade.getReceivedAt()).isEqualTo(NOW);
        assertThat(trade.getBuyerSignedAt())
                .as("지갑 서명이 없었으므로 비어 있어야 한다")
                .isNull();
    }

    @Test
    void 서명을_기록하면_그때_채워진다() {
        MaterialTrade trade = shipped();
        trade.confirmReceipt(NOW);

        trade.markBuyerSigned(NOW.plusMinutes(3));

        assertThat(trade.getBuyerSignedAt()).isEqualTo(NOW.plusMinutes(3));
    }

    @Test
    void 같은_서명을_두_번_보고해도_첫_시각이_정본이다() {
        MaterialTrade trade = shipped();
        trade.confirmReceipt(NOW);
        trade.markBuyerSigned(NOW.plusMinutes(3));

        trade.markBuyerSigned(NOW.plusHours(5));

        assertThat(trade.getBuyerSignedAt()).isEqualTo(NOW.plusMinutes(3));
    }

    @Test
    void 발송_전에는_수령을_확인할_수_없다() {
        MaterialTrade trade = created();

        assertThatThrownBy(() -> trade.confirmReceipt(NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    private MaterialTrade created() {
        return MaterialTrade.create(
                "TRD-0001", 1L, 1L, 9L, 2L, "0x1111111111111111111111111111111111111111",
                "0x2222222222222222222222222222222222222222",
                10_000L, 10_000L, PaymentMethod.MOCK, NOW.plusDays(1));
    }

    /** 발송까지 끝난 거래. 수령 확인을 받을 수 있는 상태다. */
    private MaterialTrade shipped() {
        MaterialTrade trade = created();
        trade.markPaid("pg-ref", 10_000L, NOW, PaymentMethod.MOCK);
        trade.ship(NOW);
        return trade;
    }
}
