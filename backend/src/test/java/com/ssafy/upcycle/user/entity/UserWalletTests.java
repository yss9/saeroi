package com.ssafy.upcycle.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class UserWalletTests {

    private static final String CHECKSUM = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045";
    private static final String LOWER = "0xd8da6bf26964af9d7eed9e03e53415d37aa96045";

    @Test
    void linkStoresTheAddressInLowerCase() {
        UserWallet wallet = UserWallet.link(1L, CHECKSUM, 1337L);

        assertThat(wallet.getWalletAddress()).isEqualTo(LOWER);
    }

    @Test
    void linkStartsUnverified() {
        UserWallet wallet = UserWallet.link(1L, LOWER, 1337L);

        assertThat(wallet.isVerified()).isFalse();
        assertThat(wallet.getVerifiedAt()).isNull();
    }

    @Test
    void linkRejectsMalformedAddress() {
        assertThatThrownBy(() -> UserWallet.link(1L, "0x123", 1337L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void linkRejectsNonPositiveChainId() {
        assertThatThrownBy(() -> UserWallet.link(1L, LOWER, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void markVerifiedSetsBothFields() {
        UserWallet wallet = UserWallet.link(1L, LOWER, 1337L);
        LocalDateTime at = LocalDateTime.of(2026, 9, 4, 12, 0);

        wallet.markVerified(at);

        assertThat(wallet.isVerified()).isTrue();
        assertThat(wallet.getVerifiedAt()).isEqualTo(at);
    }

    @Test
    void markVerifiedIsIdempotentAndRefreshesTheTimestamp() {
        UserWallet wallet = UserWallet.link(1L, LOWER, 1337L);
        wallet.markVerified(LocalDateTime.of(2026, 9, 4, 12, 0));

        LocalDateTime later = LocalDateTime.of(2026, 9, 4, 13, 0);
        wallet.markVerified(later);

        assertThat(wallet.isVerified()).isTrue();
        assertThat(wallet.getVerifiedAt()).isEqualTo(later);
    }
}
