package com.ssafy.upcycle.user.dto;

import com.ssafy.upcycle.user.service.IssuedTokenPair;

public record TokenRefreshResponse(String accessToken, String refreshToken) {

    public static TokenRefreshResponse from(IssuedTokenPair tokens) {
        return new TokenRefreshResponse(tokens.accessToken(), tokens.refreshToken());
    }
}
