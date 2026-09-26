package com.ssafy.upcycle.user;

import com.ssafy.upcycle.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum TokenRefreshErrorCode implements ErrorCode {
    INVALID_REFRESH_TOKEN(
            HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "유효하지 않은 Refresh Token입니다."),
    REFRESH_TOKEN_EXPIRED(
            HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_EXPIRED", "Refresh Token이 만료되었습니다."),
    USER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "유효하지 않은 Refresh Token입니다."),
    USER_SUSPENDED(HttpStatus.FORBIDDEN, "USER_SUSPENDED", "이용이 정지된 계정입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    TokenRefreshErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
