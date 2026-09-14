package com.ssafy.upcycle.user.service;

import com.ssafy.upcycle.user.config.RefreshTokenProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Refresh Token 원문을 JavaScript에 노출하지 않고 브라우저 쿠키로 전달한다. */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookieWriter {

    public static final String COOKIE_NAME = "refresh_token";
    public static final String COOKIE_PATH = "/api/v1/auth";

    private final RefreshTokenProperties properties;

    public void write(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite(properties.cookieSameSite())
                .path(COOKIE_PATH)
                .maxAge(properties.ttl())
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
