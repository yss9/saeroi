package com.ssafy.upcycle.passport;

import com.ssafy.upcycle.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PassportErrorCode implements ErrorCode {

    /**
     * qrToken 이 어떤 제품 개체와도 일치하지 않는 경우.
     *
     * <p>존재하지 않는 개체와 "그런 토큰은 없다" 를 구분해 알려주지 않는다 — 이 경로는
     * 인증이 없어 토큰 자체가 유일한 접근 통제다. 실패 사유를 자세히 알려주면 토큰을
     * 무작위로 시도하는 쪽에 힌트를 주는 셈이다.
     */
    QR_TOKEN_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "QR_TOKEN_NOT_FOUND",
            "일치하는 제품을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    PassportErrorCode(HttpStatus status, String code, String message) {
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
