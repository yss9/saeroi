package com.ssafy.upcycle.user;

import com.ssafy.upcycle.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CompanyRoleApprovalErrorCode implements ErrorCode {
    COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "사업자 계정을 찾을 수 없습니다."),
    APPROVAL_REQUIRED(HttpStatus.FORBIDDEN, "COMPANY_ROLE_APPROVAL_REQUIRED", "관리자 역할 승인이 완료된 뒤 이용할 수 있습니다."),
    WALLET_REQUIRED(HttpStatus.CONFLICT, "COMPANY_WALLET_REQUIRED", "승인 전에 검증된 지갑을 등록해야 합니다."),
    BLOCKCHAIN_NOT_CONFIGURED(HttpStatus.NOT_IMPLEMENTED, "BLOCKCHAIN_NOT_CONFIGURED", "온체인 역할 검증 환경이 설정되지 않았습니다."),
    BLOCKCHAIN_ROLE_VERIFICATION_FAILED(HttpStatus.BAD_GATEWAY, "BLOCKCHAIN_ROLE_VERIFICATION_FAILED", "관리자 지갑이 부여한 온체인 역할을 확인하지 못했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    CompanyRoleApprovalErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String message() { return message; }
}
