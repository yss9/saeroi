package com.ssafy.upcycle.user;

import com.ssafy.upcycle.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 이메일과 비밀번호를 사용하는 인증의 실패 사유. */
public enum EmailAuthErrorCode implements ErrorCode {
    INVALID_CREDENTIALS(
            HttpStatus.UNAUTHORIZED,
            "INVALID_CREDENTIALS",
            "이메일 또는 비밀번호가 올바르지 않습니다."),
    USER_SUSPENDED(HttpStatus.FORBIDDEN, "USER_SUSPENDED", "이용이 정지된 계정입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    EmailAuthErrorCode(HttpStatus status, String code, String message) {
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
