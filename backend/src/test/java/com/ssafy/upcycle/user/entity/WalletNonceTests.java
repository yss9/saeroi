package com.ssafy.upcycle.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class WalletNonceTests {

    private static final String LOWER = "0xd8da6bf26964af9d7eed9e03e53415d37aa96045";
    private static final String NONCE = "0123456789abcdef0123456789abcdef";
    private static final LocalDateTime EXPIRES = LocalDateTime.of(2026, 9, 4, 12, 5);

    @Test
    void issueNormalizesTheAddress() {
        WalletNonce nonce =
                WalletNonce.issue(1L, "0xD8DA6BF26964AF9D7EED9E03E53415D37AA96045", 1337L, NONCE, EXPIRES);

        assertThat(nonce.getWalletAddress()).isEqualTo(LOWER);
    }

    @Test
    void issueRejectsNonceOfWrongLength() {
        assertThatThrownBy(() -> WalletNonce.issue(1L, LOWER, 1337L, "tooshort", EXPIRES))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void issueRejectsNonPositiveChainId() {
        assertThatThrownBy(() -> WalletNonce.issue(1L, LOWER, -1L, NONCE, EXPIRES))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void freshNonceIsNotUsed() {
        assertThat(WalletNonce.issue(1L, LOWER, 1337L, NONCE, EXPIRES).isUsed()).isFalse();
    }

    @Test
    void expiryBoundaryCountsAsExpired() {
        WalletNonce nonce = WalletNonce.issue(1L, LOWER, 1337L, NONCE, EXPIRES);

        assertThat(nonce.isExpiredAt(EXPIRES.minusSeconds(1))).isFalse();
        assertThat(nonce.isExpiredAt(EXPIRES)).isTrue();
        assertThat(nonce.isExpiredAt(EXPIRES.plusSeconds(1))).isTrue();
    }
}
