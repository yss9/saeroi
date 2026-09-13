package com.ssafy.upcycle.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class RefreshTokenTests {

    private static final String HASH = "a".repeat(RefreshToken.HASH_LENGTH);

    @Test
    void issuesAnActiveTokenAndExpiresAtTheExactBoundary() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 9, 20, 12, 0);
        RefreshToken token = RefreshToken.issue(1L, HASH, expiresAt);

        assertThat(token.isRevoked()).isFalse();
        assertThat(token.isExpiredAt(expiresAt.minusNanos(1))).isFalse();
        assertThat(token.isExpiredAt(expiresAt)).isTrue();
    }

    @Test
    void rejectsInvalidRequiredFields() {
        LocalDateTime expiry = LocalDateTime.now().plusDays(1);

        assertThatThrownBy(() -> RefreshToken.issue(null, HASH, expiry))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshToken.issue(1L, "short", expiry))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshToken.issue(1L, HASH, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
