package com.ssafy.upcycle.user.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RefreshTokenPropertiesTests {

    @Test
    void acceptsAPositiveTtl() {
        RefreshTokenProperties properties =
                new RefreshTokenProperties(Duration.ofDays(14), true, "Strict");

        assertThat(properties.ttl()).isEqualTo(Duration.ofDays(14));
        assertThat(properties.cookieSecure()).isTrue();
        assertThat(properties.cookieSameSite()).isEqualTo("Strict");
    }

    @Test
    void rejectsMissingOrNonPositiveTtl() {
        assertThatThrownBy(() -> new RefreshTokenProperties(null, true, "Strict"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefreshTokenProperties(Duration.ZERO, true, "Strict"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new RefreshTokenProperties(Duration.ofSeconds(-1), true, "Strict"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnsupportedOrInsecureCrossSiteCookieSettings() {
        assertThatThrownBy(() ->
                        new RefreshTokenProperties(Duration.ofDays(14), true, "invalid"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new RefreshTokenProperties(Duration.ofDays(14), false, "None"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
