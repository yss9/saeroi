package com.ssafy.upcycle.user;

import com.ssafy.upcycle.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 회원가입 실패 사유. */
public enum SignupErrorCode implements ErrorCode {
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 가입된 이메일입니다."),
    INVALID_BUSINESS_ROLE(
            HttpStatus.BAD_REQUEST,
            "INVALID_BUSINESS_ROLE",
            "사업자회원으로 가입할 수 없는 사용자 역할입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    SignupErrorCode(HttpStatus status, String code, String message) {
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
