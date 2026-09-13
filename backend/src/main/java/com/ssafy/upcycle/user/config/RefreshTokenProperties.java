package com.ssafy.upcycle.user.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.refresh-token")
public final class RefreshTokenProperties {

    private final Duration ttl;

    public RefreshTokenProperties(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Refresh token TTL must be positive");
        }
        this.ttl = ttl;
    }

    public Duration ttl() {
        return ttl;
    }
}
