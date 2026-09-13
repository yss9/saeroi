package com.ssafy.upcycle.user.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.refresh-token")
public final class RefreshTokenProperties {

    private final Duration ttl;
    private final boolean cookieSecure;
    private final String cookieSameSite;

    public RefreshTokenProperties(Duration ttl, boolean cookieSecure, String cookieSameSite) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Refresh token TTL must be positive");
        }
        if (!"Strict".equals(cookieSameSite)
                && !"Lax".equals(cookieSameSite)
                && !"None".equals(cookieSameSite)) {
            throw new IllegalArgumentException(
                    "Refresh token cookie SameSite must be Strict, Lax, or None");
        }
        if ("None".equals(cookieSameSite) && !cookieSecure) {
            throw new IllegalArgumentException(
                    "SameSite=None refresh token cookies must be Secure");
        }
        this.ttl = ttl;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
    }

    public Duration ttl() {
        return ttl;
    }

    public boolean cookieSecure() {
        return cookieSecure;
    }

    public String cookieSameSite() {
        return cookieSameSite;
    }
}
