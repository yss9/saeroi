package com.ssafy.upcycle.user.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 지갑 서명 로그인 설정.
 *
 * <p>{@link com.ssafy.upcycle.common.security.config.JwtProperties} 와 같은 방식으로
 * 생성자 바인딩 + 즉시 검증한다. 잘못된 값은 기동 시점에 터지는 편이 낫다 —
 * TTL 이 0 이면 발급된 nonce 가 전부 즉시 만료되어 로그인이 조용히 전멸한다.
 */
@ConfigurationProperties(prefix = "app.auth.wallet")
public final class WalletAuthProperties {

    private final Duration nonceTtl;

    public WalletAuthProperties(Duration nonceTtl) {
        if (nonceTtl == null || nonceTtl.isZero() || nonceTtl.isNegative()) {
            throw new IllegalArgumentException("Wallet nonce TTL must be positive");
        }
        this.nonceTtl = nonceTtl;
    }

    public Duration nonceTtl() {
        return nonceTtl;
    }
}
