package com.ssafy.upcycle.processing;

import com.ssafy.upcycle.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ProcessingErrorCode implements ErrorCode {
    PROCESSOR_PROFILE_FORBIDDEN(HttpStatus.FORBIDDEN, "가공업체 계정만 업체 정보를 등록하거나 조회할 수 있습니다."),
    PROCESSOR_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "등록된 가공업체 프로필을 찾을 수 없습니다."),
    PROCESSOR_CAPABILITIES_INVALID(HttpStatus.BAD_REQUEST, "소재·공정별 최소/최대 수량을 확인해 주세요. 수량은 양의 정수이며 동일 조합은 한 번만 등록할 수 있습니다."),
    PROCESSOR_LEAD_TIMES_INVALID(HttpStatus.BAD_REQUEST, "수량 구간과 소요일을 확인해 주세요. 최대 수량은 중복할 수 없고 모든 가공 조건을 포함해야 합니다."),
    PROCESSING_FORBIDDEN(HttpStatus.FORBIDDEN, "해당 가공 요청을 처리할 권한이 없습니다."),
    PROCESSING_MATERIAL_NOT_FOUND(HttpStatus.NOT_FOUND, "사용 가능한 소재를 찾을 수 없습니다."),
    PROCESSING_PROCESSOR_NOT_FOUND(HttpStatus.NOT_FOUND, "활성 가공업체를 찾을 수 없습니다."),
    PROCESSING_CAPABILITY_MISMATCH(HttpStatus.BAD_REQUEST, "업체가 소재·가공 목적·수량 조건을 모두 충족하지 않습니다. 조건을 완화하거나 다른 업체를 선택해 주세요."),
    PROCESSING_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "소재, 업체, 양의 정수 g 수량과 중복 없는 가공 목적이 필요합니다."),
    PROCESSING_INVALID_QUERY(HttpStatus.BAD_REQUEST, "가공 요청 목록 조회 조건이 올바르지 않습니다."),
    PROCESSING_INSUFFICIENT_BALANCE(HttpStatus.CONFLICT, "활성 Lock을 제외한 소재 보유 수량이 부족합니다."),
    PROCESSING_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "가공 요청을 찾을 수 없습니다."),
    PROCESSING_INVALID_STATE(HttpStatus.CONFLICT, "현재 상태에서는 가공 요청을 변경할 수 없습니다."),
    PROCESSING_QUOTE_NOT_FOUND(HttpStatus.NOT_FOUND, "가공 견적을 찾을 수 없습니다."),
    PROCESSING_QUOTE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이 요청에는 이미 견적을 제출했습니다."),
    PROCESSING_INVALID_QUOTE(HttpStatus.BAD_REQUEST, "견적 가격은 0 이상의 정수, 작업 기간은 양의 정수여야 합니다."),
    PROCESSING_JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "가공 작업을 찾을 수 없습니다."),
    PROCESSING_WALLET_NOT_FOUND(HttpStatus.CONFLICT, "Brand와 Processor 모두 활성 지갑이 필요합니다."),
    PROCESSING_PAYMENT_NOT_PREPARED(HttpStatus.CONFLICT, "결제 준비가 필요합니다."),
    PROCESSING_PAYMENT_EXPIRED(HttpStatus.CONFLICT, "결제 가능 시간이 만료되었습니다."),
    PROCESSING_PAYMENT_AMOUNT_MISMATCH(HttpStatus.CONFLICT, "승인 금액이 견적 금액과 일치하지 않습니다.");

    private final HttpStatus status;
    private final String message;
    ProcessingErrorCode(HttpStatus status, String message) { this.status = status; this.message = message; }
    public HttpStatus status() { return status; }
    public String code() { return name(); }
    public String message() { return message; }
}
