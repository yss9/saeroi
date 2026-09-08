package com.ssafy.upcycle.product.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.upcycle.trade.entity.PaymentMethod;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ProductOrderTests {

    private static final long BUYER_ID = 10L;
    private static final long BRAND_ID = 11L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 8, 12, 0);

    @Test
    void placedOrderStartsAsCreated() {
        ProductOrder order = order();

        assertThat(order.getStatus()).isEqualTo(ProductOrderStatus.CREATED);
        assertThat(order.getPaymentAmount()).isZero();
        assertThat(order.getPaidAt()).isNull();
    }

    @Test
    void buyerAndBrandMustDiffer() {
        assertThatThrownBy(() -> ProductOrder.place(
                        "ORD-000001", BUYER_ID, BUYER_ID, 89_000L,
                        PaymentMethod.MOCK, NOW.plusMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeTotalPriceIsRejected() {
        assertThatThrownBy(() -> ProductOrder.place(
                        "ORD-000001", BUYER_ID, BRAND_ID, -1L,
                        PaymentMethod.MOCK, NOW.plusMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void orderWalksTheHappyPathInOrder() {
        ProductOrder order = order();

        order.markPaid("T1234", 89_000L, NOW);
        assertThat(order.getStatus()).isEqualTo(ProductOrderStatus.PAID);
        assertThat(order.getPaymentReference()).isEqualTo("T1234");

        order.ship(NOW.plusDays(1));
        assertThat(order.getStatus()).isEqualTo(ProductOrderStatus.SHIPPED);

        order.confirmDelivery(NOW.plusDays(3));
        assertThat(order.getStatus()).isEqualTo(ProductOrderStatus.DELIVERED);
        assertThat(order.getDeliveredAt()).isEqualTo(NOW.plusDays(3));
    }

    /** 결제 전에 발송하면 대금 없이 물건이 나간다. */
    @Test
    void unpaidOrderCannotShip() {
        assertThatThrownBy(() -> order().ship(NOW)).isInstanceOf(IllegalStateException.class);
    }

    /**
     * 브랜드의 발송만으로 주문이 완료되지 않는다. 소비자의 수령 확인이 있어야 한다 —
     * 소재 거래와 같은 2-Track 이다.
     */
    @Test
    void unshippedOrderCannotBeDelivered() {
        ProductOrder order = order();
        order.markPaid("T1234", 89_000L, NOW);

        assertThatThrownBy(() -> order.confirmDelivery(NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deliveryIsConfirmedOnce() {
        ProductOrder order = shippedOrder();
        order.confirmDelivery(NOW);

        assertThatThrownBy(() -> order.confirmDelivery(NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    /** 물건이 나간 뒤 주문만 없애면 그 개체가 어디에도 속하지 않게 된다. */
    @Test
    void onlyUnshippedOrdersAreCancelable() {
        ProductOrder created = order();
        assertThat(created.isCancelable()).isTrue();

        created.markPaid("T1234", 89_000L, NOW);
        assertThat(created.isCancelable()).isTrue();

        created.ship(NOW);
        assertThat(created.isCancelable()).isFalse();

        created.confirmDelivery(NOW);
        assertThat(created.isCancelable()).isFalse();
    }

    /** 마감을 넘긴 미결제 주문이 정리되지 않으면 개체가 영구히 RESERVED 로 묶인다. */
    @Test
    void unpaidOrderExpiresAfterItsDeadline() {
        ProductOrder order = order();

        assertThat(order.isPaymentExpiredAt(NOW.plusMinutes(29))).isFalse();
        assertThat(order.isPaymentExpiredAt(NOW.plusMinutes(30))).isTrue();
    }

    /** 결제까지 끝난 주문은 마감이 지나도 만료가 아니다. */
    @Test
    void paidOrderNeverExpires() {
        ProductOrder order = order();
        order.markPaid("T1234", 89_000L, NOW);

        assertThat(order.isPaymentExpiredAt(NOW.plusDays(1))).isFalse();
    }

    @Test
    void orderWithoutADeadlineNeverExpires() {
        ProductOrder order = ProductOrder.place(
                "ORD-000001", BUYER_ID, BRAND_ID, 89_000L, PaymentMethod.MOCK, null);

        assertThat(order.isPaymentExpiredAt(NOW.plusYears(1))).isFalse();
    }

    private ProductOrder order() {
        return ProductOrder.place(
                "ORD-000001", BUYER_ID, BRAND_ID, 89_000L,
                PaymentMethod.MOCK, NOW.plusMinutes(30));
    }

    private ProductOrder shippedOrder() {
        ProductOrder order = order();
        order.markPaid("T1234", 89_000L, NOW);
        order.ship(NOW);
        return order;
    }
}
