package com.ssafy.upcycle.trade.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TradeShippingTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 4, 12, 0);

    @Test
    void shippingStartsWithAddressOnly() {
        TradeShipping shipping = shipping();

        assertThat(shipping.getCarrier()).isNull();
        assertThat(shipping.getTrackingNumber()).isNull();
        assertThat(shipping.getShippedAt()).isNull();
        assertThat(shipping.getDeliveredAt()).isNull();
    }

    @Test
    void addressDetailIsOptional() {
        TradeShipping shipping = TradeShipping.of(
                1L, "김구매", "010-0000-0000", "04766", "서울시 성동구", null);

        assertThat(shipping.getAddressDetail()).isNull();
    }

    @Test
    void missingRequiredAddressFieldsAreRejected() {
        assertThatThrownBy(() -> TradeShipping.of(
                        1L, "  ", "010-0000-0000", "04766", "서울시 성동구", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TradeShipping.of(
                        1L, "김구매", "010-0000-0000", "04766", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shipmentRecordsCarrierAndTrackingNumber() {
        TradeShipping shipping = shipping();

        shipping.registerShipment("CJ대한통운", "123456789012", NOW);

        assertThat(shipping.getCarrier()).isEqualTo("CJ대한통운");
        assertThat(shipping.getTrackingNumber()).isEqualTo("123456789012");
        assertThat(shipping.getShippedAt()).isEqualTo(NOW);
    }

    /** 직접 수령·화물처럼 송장이 없는 전달도 있다. */
    @Test
    void shipmentWithoutTrackingNumberIsAllowed() {
        TradeShipping shipping = shipping();

        shipping.registerShipment(null, null, NOW);

        assertThat(shipping.getShippedAt()).isEqualTo(NOW);
        assertThat(shipping.getTrackingNumber()).isNull();
    }

    @Test
    void shipmentCannotBeRegisteredTwice() {
        TradeShipping shipping = shipping();
        shipping.registerShipment("CJ대한통운", "123456789012", NOW);

        assertThatThrownBy(() -> shipping.registerShipment("우체국", "999", NOW.plusDays(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deliveryRequiresShipmentFirst() {
        TradeShipping shipping = shipping();

        assertThatThrownBy(() -> shipping.markDelivered(NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deliveryIsRecordedOnce() {
        TradeShipping shipping = shipping();
        shipping.registerShipment("CJ대한통운", "123456789012", NOW);

        shipping.markDelivered(NOW.plusDays(2));
        assertThat(shipping.getDeliveredAt()).isEqualTo(NOW.plusDays(2));

        assertThatThrownBy(() -> shipping.markDelivered(NOW.plusDays(3)))
                .isInstanceOf(IllegalStateException.class);
    }

    private TradeShipping shipping() {
        return TradeShipping.of(
                1L, "김구매", "010-0000-0000", "04766", "서울시 성동구", "101동 1001호");
    }
}
