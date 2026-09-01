package com.ssafy.upcycle.common.security.jwt;

import java.time.Instant;
import java.util.Objects;

public final class AccessToken {

    private final String value;
    private final Instant expiresAt;

    public AccessToken(String value, Instant expiresAt) {
        this.value = Objects.requireNonNull(value);
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }

    public String value() {
        return value;
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}
