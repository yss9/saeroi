package com.ssafy.upcycle.material.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MaterialBalanceTests {

    private static final String WALLET = "0x1234567890abcdef1234567890abcdef12345678";

    @Test
    void balanceStartsWithTheGivenQuantity() {
        MaterialBalance balance = MaterialBalance.of(1L, 2L, WALLET, 20_000L);

        assertThat(balance.getMaterialId()).isEqualTo(1L);
        assertThat(balance.getOwnerId()).isEqualTo(2L);
        assertThat(balance.getQuantity()).isEqualTo(20_000L);
    }

    @Test
    void zeroQuantityIsAllowedButNegativeIsNot() {
        assertThat(MaterialBalance.of(1L, 2L, WALLET, 0L).getQuantity()).isZero();

        assertThatThrownBy(() -> MaterialBalance.of(1L, 2L, WALLET, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void malformedWalletAddressIsRejected() {
        assertThatThrownBy(() -> MaterialBalance.of(1L, 2L, "0x1234", 20_000L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MaterialBalance.of(1L, 2L, "  ", 20_000L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MaterialBalance.of(
                        1L, 2L, "1234567890abcdef1234567890abcdef12345678", 20_000L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void increaseAndDecreaseMoveTheQuantity() {
        MaterialBalance balance = MaterialBalance.of(1L, 2L, WALLET, 20_000L);

        balance.increase(5_000L);
        assertThat(balance.getQuantity()).isEqualTo(25_000L);

        balance.decrease(10_000L);
        assertThat(balance.getQuantity()).isEqualTo(15_000L);
    }

    @Test
    void quantityCannotGoNegative() {
        MaterialBalance balance = MaterialBalance.of(1L, 2L, WALLET, 20_000L);

        assertThatThrownBy(() -> balance.decrease(20_001L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(balance.getQuantity()).isEqualTo(20_000L);
    }

    @Test
    void nonPositiveMovementsAreRejected() {
        MaterialBalance balance = MaterialBalance.of(1L, 2L, WALLET, 20_000L);

        assertThatThrownBy(() -> balance.increase(0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> balance.decrease(0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> balance.increase(-1L)).isInstanceOf(IllegalArgumentException.class);
    }
}
