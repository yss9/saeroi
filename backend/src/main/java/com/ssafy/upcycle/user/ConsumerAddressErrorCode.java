package com.ssafy.upcycle.user;

import com.ssafy.upcycle.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 소비자 배송지(consumer_address) 조회·수정 실패 사유.
 *
 * <p>{@code common} 은 도메인을 알면 안 되므로 도메인 에러 코드는 여기 둔다
 * ({@code CompanyErrorCode} 와 같은 모양).
 */
public enum ConsumerAddressErrorCode implements ErrorCode {
    CONSUMER_ADDRESS_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "CONSUMER_ADDRESS_FORBIDDEN",
            "배송지를 이용할 수 없는 계정입니다."),

    /**
     * 남의 배송지를 가리켰을 때도 이것을 쓴다.
     *
     * <p>403 으로 답하면 "그 id 는 존재하고 남의 것" 이라는 사실이 새어 나간다. id 를 훑어
     * 주소록 크기나 다른 사람의 등록 여부를 알아낼 길이 생기므로, 없는 것과 남의 것을 같은
     * 답으로 덮는다.
     */
    CONSUMER_ADDRESS_NOT_FOUND(
            HttpStatus.NOT_FOUND, "CONSUMER_ADDRESS_NOT_FOUND", "등록된 배송지를 찾을 수 없습니다."),

    CONSUMER_ADDRESS_LIMIT_EXCEEDED(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "CONSUMER_ADDRESS_LIMIT_EXCEEDED",
            "배송지는 최대 20개까지 등록할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ConsumerAddressErrorCode(HttpStatus status, String code, String message) {
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
