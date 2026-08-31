package com.ssafy.upcycle.common.security.config;

import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.jwt")
public final class JwtProperties {

    private static final int MINIMUM_KEY_BYTES = 32;

    private final SecretKey secretKey;
    private final String issuer;
    private final Duration accessTokenTtl;

    public JwtProperties(String secret, String issuer, Duration accessTokenTtl) {
        this.secretKey = decodeSecret(secret);
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("JWT issuer must not be blank");
        }
        if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException("JWT access token TTL must be positive");
        }
        this.issuer = issuer;
        this.accessTokenTtl = accessTokenTtl;
    }

    public SecretKey secretKey() {
        return new SecretKeySpec(secretKey.getEncoded(), "HmacSHA256");
    }

    public String issuer() {
        return issuer;
    }

    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    private static SecretKey decodeSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must not be blank");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("JWT secret must be valid Base64", exception);
        }
        if (decoded.length < MINIMUM_KEY_BYTES) {
            throw new IllegalArgumentException("JWT secret must decode to at least 32 bytes");
        }
        return new SecretKeySpec(decoded, "HmacSHA256");
    }
}
