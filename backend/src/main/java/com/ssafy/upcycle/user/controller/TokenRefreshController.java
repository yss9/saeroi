package com.ssafy.upcycle.user.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.user.dto.TokenRefreshResponse;
import com.ssafy.upcycle.user.service.IssuedTokenPair;
import com.ssafy.upcycle.user.service.RefreshTokenCookieWriter;
import com.ssafy.upcycle.user.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class TokenRefreshController {

    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenCookieWriter refreshTokenCookieWriter;

    @PostMapping("/refresh")
    public ApiResponse<TokenRefreshResponse> refresh(
            @CookieValue(name = RefreshTokenCookieWriter.COOKIE_NAME, required = false)
                    String refreshToken,
            HttpServletResponse response) {
        IssuedTokenPair tokens = refreshTokenService.refresh(refreshToken);
        refreshTokenCookieWriter.write(response, tokens.refreshToken());
        return ApiResponse.success(TokenRefreshResponse.from(tokens));
    }
}
