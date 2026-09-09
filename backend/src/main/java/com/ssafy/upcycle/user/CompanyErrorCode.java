package com.ssafy.upcycle.user;

import com.ssafy.upcycle.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 업체 정보(company_profile) 조회·수정 실패 사유.
 *
 * <p>{@code common} 은 도메인을 알면 안 되므로 도메인 에러 코드는 여기 둔다
 * ({@code SignupErrorCode}·{@code WalletAuthErrorCode} 와 같은 모양).
 */
public enum CompanyErrorCode implements ErrorCode {
    COMPANY_PROFILE_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "COMPANY_PROFILE_FORBIDDEN",
            "업체 정보를 이용할 수 없는 계정입니다."),
    COMPANY_PROFILE_NOT_FOUND(
            HttpStatus.NOT_FOUND, "COMPANY_PROFILE_NOT_FOUND", "등록된 업체 정보를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    CompanyErrorCode(HttpStatus status, String code, String message) {
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
