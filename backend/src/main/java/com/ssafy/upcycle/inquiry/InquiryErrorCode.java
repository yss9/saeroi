package com.ssafy.upcycle.inquiry;

import com.ssafy.upcycle.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 소비자 문의(consumer_inquiry) 등록·조회 실패 사유.
 *
 * <p>{@code common} 은 도메인을 알면 안 되므로 도메인 에러 코드는 여기 둔다
 * ({@code ConsumerAddressErrorCode} 와 같은 모양).
 */
public enum InquiryErrorCode implements ErrorCode {
    CONSUMER_INQUIRY_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "CONSUMER_INQUIRY_FORBIDDEN",
            "문의를 등록할 수 없는 계정입니다."),

    /**
     * 남의 문의를 가리켰을 때도 이것을 쓴다.
     *
     * <p>{@code CONSUMER_ADDRESS_NOT_FOUND} 와 같은 이유 — 있음/없음과 남의 것을 같은 답으로
     * 덮어야 존재 여부가 새어 나가지 않는다.
     */
    CONSUMER_INQUIRY_NOT_FOUND(
            HttpStatus.NOT_FOUND, "CONSUMER_INQUIRY_NOT_FOUND", "문의를 찾을 수 없습니다."),

    /**
     * {@code productOrderId} 가 존재하지 않거나 남의 주문일 때 이 코드다. 있음/없음과
     * 남의 것을 같은 답으로 덮어야 존재 여부가 새어 나가지 않는다.
     */
    INQUIRY_ORDER_NOT_FOUND(
            HttpStatus.NOT_FOUND, "INQUIRY_ORDER_NOT_FOUND", "문의 대상 주문을 찾을 수 없습니다."),

    /** {@code ETC} 가 아닌 유형인데 {@code productOrderId} 를 아예 보내지 않았을 때. */
    INQUIRY_TARGET_REQUIRED(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "INQUIRY_TARGET_REQUIRED",
            "문의 대상 주문을 선택해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    InquiryErrorCode(HttpStatus status, String code, String message) {
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
