package com.ssafy.upcycle.processing;

import com.ssafy.upcycle.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ProcessingErrorCode implements ErrorCode {
    PROCESSING_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 가공 요청을 처리할 권한이 없습니다."),
    PROCESSING_MATERIAL_NOT_FOUND(HttpStatus.NOT_FOUND, "사용 가능한 소재를 찾을 수 없습니다."),
    PROCESSING_PROCESSOR_NOT_FOUND(HttpStatus.NOT_FOUND, "활성 가공업체를 찾을 수 없습니다."),
    PROCESSING_CAPABILITY_MISMATCH(HttpStatus.BAD_REQUEST, "업체가 소재·가공 목적·수량 조건을 모두 충족하지 않습니다. 조건을 완화하거나 다른 업체를 선택해 주세요."),
    PROCESSING_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "소재, 업체, 양의 정수 g 수량과 중복 없는 가공 목적이 필요합니다."),
    PROCESSING_INSUFFICIENT_BALANCE(HttpStatus.CONFLICT, "활성 Lock을 제외한 소재 보유 수량이 부족합니다."),
    PROCESSING_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "가공 요청을 찾을 수 없습니다."),
    PROCESSING_INVALID_STATE(HttpStatus.CONFLICT, "현재 상태에서는 가공 요청을 변경할 수 없습니다.");

    private final HttpStatus status;
    private final String message;
    ProcessingErrorCode(HttpStatus status, String message) { this.status = status; this.message = message; }
    public HttpStatus status() { return status; }
    public String code() { return name(); }
    public String message() { return message; }
}
