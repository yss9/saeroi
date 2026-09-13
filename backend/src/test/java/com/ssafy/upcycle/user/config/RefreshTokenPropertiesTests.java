package com.ssafy.upcycle.user.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RefreshTokenPropertiesTests {

    @Test
    void acceptsAPositiveTtl() {
        RefreshTokenProperties properties = new RefreshTokenProperties(Duration.ofDays(14));

        assertThat(properties.ttl()).isEqualTo(Duration.ofDays(14));
    }

    @Test
    void rejectsMissingOrNonPositiveTtl() {
        assertThatThrownBy(() -> new RefreshTokenProperties(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefreshTokenProperties(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefreshTokenProperties(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
