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
                        SELLER_WALLET, BUYER_WALLET, 20_000L, 200_000L, PaymentMethod.MOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void malformedWalletAddressIsRejected() {
        assertThatThrownBy(() -> MaterialTrade.create(
                        "TRD-000001", 1L, 2L, 10L, 11L,
                        "0xabc", BUYER_WALLET, 20_000L, 200_000L, PaymentMethod.MOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonPositiveQuantityIsRejected() {
        assertThatThrownBy(() -> MaterialTrade.create(
                        "TRD-000001", 1L, 2L, 10L, 11L,
                        SELLER_WALLET, BUYER_WALLET, 0L, 200_000L, PaymentMethod.MOCK))
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

        trade.confirmReceipt(NOW.plusDays(3), NOW.plusDays(3));
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

        assertThatThrownBy(() -> trade.confirmReceipt(NOW, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void receiptCannotBeConfirmedTwice() {
        MaterialTrade trade = shippedTrade();
        trade.confirmReceipt(NOW, NOW);

        assertThatThrownBy(() -> trade.confirmReceipt(NOW, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    /** 수령 확인 시각과 서명 시각은 별개 컬럼이다. 정책 09.1 이 둘을 따로 요구한다. */
    @Test
    void receiptRecordsBothReceivedAndSignedTimes() {
        MaterialTrade trade = shippedTrade();

        trade.confirmReceipt(NOW, NOW.plusMinutes(5));

        assertThat(trade.getReceivedAt()).isEqualTo(NOW);
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
                PaymentMethod.MOCK);
    }

    private MaterialTrade shippedTrade() {
        MaterialTrade trade = trade();
        trade.markPaid("T1234", 200_000L, NOW, PaymentMethod.MOCK);
        trade.ship(NOW);
        return trade;
    }
}
