package com.ssafy.upcycle.common.exception;

import java.util.Objects;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * 화면이 안내를 만들려면 코드와 문구만으로 부족한 경우의 부가 정보. 없으면 {@code null}.
     *
     * <p>예를 들어 "일부 상품의 재고가 부족합니다" 는 <b>어떤 상품이</b> 부족한지까지
     * 알려야 사용자가 장바구니에서 무엇을 빼야 할지 안다. 응답 봉투에는 이미 실을 자리가
     * 있다 — {@code ApiResponse.error(errorCode, data)}.
     */
    private final transient Object details;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(ErrorCode errorCode, Object details) {
        super(Objects.requireNonNull(errorCode, "errorCode must not be null").message());
        this.errorCode = errorCode;
        this.details = details;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Object getDetails() {
        return details;
    }
}
