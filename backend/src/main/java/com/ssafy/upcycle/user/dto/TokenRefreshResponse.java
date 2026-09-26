package com.ssafy.upcycle.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ssafy.upcycle.user.service.IssuedTokenPair;

public record TokenRefreshResponse(String accessToken, @JsonIgnore String refreshToken) {

    public static TokenRefreshResponse from(IssuedTokenPair tokens) {
        return new TokenRefreshResponse(tokens.accessToken(), tokens.refreshToken());
    }
}
