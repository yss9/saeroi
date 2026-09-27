package com.ssafy.upcycle.trade.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MaterialTradeTests {

    private static final String SELLER_WALLET = "0x1111111111111111111111111111111111111111";
    private static final String BUYER_WALLET = "0x2222222222222222222222222222222222222222";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 4, 12, 0);

    @Test
    void tradeStartsAsCreatedBeforePayment() {
        MaterialTrade trade = trade();

        assertThat(trade.getStatus()).isEqualTo(TradeStatus.CREATED);
        assertThat(trade.getPaymentAmount()).isZero();
        assertThat(trade.getPaidAt()).isNull();
        assertThat(trade.getBuyerSignedAt()).isNull();
    }

    @Test
    void sellerAndBuyerMustDiffer() {
        assertThatThrownBy(() -> MaterialTrade.create(
                        "TRD-000001", 1L, 2L, 10L, 10L,
                        SELLER_WALLET, BUYER_WALLET, 20_000L, 200_000L, PaymentMethod.MOCK, LocalDateTime.now().plusMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void malformedWalletAddressIsRejected() {
        assertThatThrownBy(() -> MaterialTrade.create(
                        "TRD-000001", 1L, 2L, 10L, 11L,
                        "0xabc", BUYER_WALLET, 20_000L, 200_000L, PaymentMethod.MOCK, LocalDateTime.now().plusMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonPositiveQuantityIsRejected() {
        assertThatThrownBy(() -> MaterialTrade.create(
                        "TRD-000001", 1L, 2L, 10L, 11L,
                        SELLER_WALLET, BUYER_WALLET, 0L, 200_000L, PaymentMethod.MOCK, LocalDateTime.now().plusMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tradeWalksTheHappyPathInOrder() {
        MaterialTrade trade = trade();

        trade.markPaid("T1234", 200_000L, NOW, PaymentMethod.MOCK);
        assertThat(trade.getStatus()).isEqualTo(TradeStatus.PAID);
        assertThat(trade.getPaymentReference()).isEqualTo("T1234");
        assertThat(trade.getPaymentAmount()).isEqualTo(200_000L);

        trade.ship(NOW.plusDays(1));
        assertThat(trade.getStatus()).isEqualTo(TradeStatus.SHIPPED);
        assertThat(trade.getShippedAt()).isEqualTo(NOW.plusDays(1));

        trade.confirmReceipt(NOW.plusDays(3));
        trade.markBuyerSigned(NOW.plusDays(3));
        assertThat(trade.getStatus()).isEqualTo(TradeStatus.RECEIVED);

        trade.complete(NOW.plusDays(3));
        assertThat(trade.getStatus()).isEqualTo(TradeStatus.COMPLETED);
        assertThat(trade.getCompletedAt()).isEqualTo(NOW.plusDays(3));
    }

    /** 결제 전에 발송하면 대금 없이 물건이 나간다. */
    @Test
    void unpaidTradeCannotShip() {
        MaterialTrade trade = trade();

        assertThatThrownBy(() -> trade.ship(NOW)).isInstanceOf(IllegalStateException.class);
    }

    /** 배송하지 않은 건을 수령 확인하면 소유권이 근거 없이 넘어간다. */
    @Test
    void unshippedTradeCannotConfirmReceipt() {
        MaterialTrade trade = trade();
        trade.markPaid("T1234", 200_000L, NOW, PaymentMethod.MOCK);

        assertThatThrownBy(() -> trade.confirmReceipt(NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void receiptCannotBeConfirmedTwice() {
        MaterialTrade trade = shippedTrade();
        trade.confirmReceipt(NOW);
        trade.markBuyerSigned(NOW);

        assertThatThrownBy(() -> trade.confirmReceipt(NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 수령 확인 시각과 서명 시각은 별개 컬럼이고 <b>따로 채워진다.</b>
     *
     * <p>예전에는 수령 확인이 둘을 함께 받았다. 그래서 지갑 서명을 한 번도 하지 않은
     * 거래가 "검증됨" 으로 남았고, 체인에서는 소재가 판매자 잠금에 그대로 있었다.
     */
    @Test
    void receiptRecordsReceivedTimeOnly() {
        MaterialTrade trade = shippedTrade();

        trade.confirmReceipt(NOW);

        assertThat(trade.getReceivedAt()).isEqualTo(NOW);
        assertThat(trade.getBuyerSignedAt()).isNull();

        trade.markBuyerSigned(NOW.plusMinutes(5));
        assertThat(trade.getBuyerSignedAt()).isEqualTo(NOW.plusMinutes(5));
    }

    @Test
    void chainTradeIdIsAssignedOnlyOnce() {
        MaterialTrade trade = trade();

        trade.assignChainTradeId(7L);
        assertThat(trade.getChainTradeId()).isEqualTo(7L);

        assertThatThrownBy(() -> trade.assignChainTradeId(8L))
                .isInstanceOf(IllegalStateException.class);
    }

    private MaterialTrade trade() {
        return MaterialTrade.create(
                "TRD-000001",
                1L,
                2L,
                10L,
                11L,
                SELLER_WALLET,
                BUYER_WALLET,
                20_000L,
                200_000L,
                PaymentMethod.MOCK, LocalDateTime.now().plusMinutes(30));
    }

    private MaterialTrade shippedTrade() {
        MaterialTrade trade = trade();
        trade.markPaid("T1234", 200_000L, NOW, PaymentMethod.MOCK);
        trade.ship(NOW);
        return trade;
    }
}
