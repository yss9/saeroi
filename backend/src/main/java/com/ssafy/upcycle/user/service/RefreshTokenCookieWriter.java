package com.ssafy.upcycle.user.service;

import com.ssafy.upcycle.user.config.RefreshTokenProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
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

    /** 로그인 때 사용한 이름과 경로로 만료 쿠키를 내려 브라우저의 Refresh Token을 삭제한다. */
    public void clear(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite(properties.cookieSameSite())
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
