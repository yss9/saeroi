package com.ssafy.upcycle.user.service;

/** 로그인 또는 재발급 결과. 로그에 토큰을 노출할 수 있어 의도적으로 toString을 재정의하지 않는다. */
public final class IssuedTokenPair {

    private final String accessToken;
    private final String refreshToken;

    public IssuedTokenPair(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public String accessToken() {
        return accessToken;
    }

    public String refreshToken() {
        return refreshToken;
    }
}
