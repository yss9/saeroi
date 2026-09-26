package com.ssafy.upcycle.vision;

import com.ssafy.upcycle.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 저울 사진 인식 서비스와 통신하지 못했을 때의 공개 오류 계약. */
public enum VisionErrorCode implements ErrorCode {
    VISION_SERVICE_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "VISION_SERVICE_UNAVAILABLE",
            "저울 사진 인식 서비스를 사용할 수 없습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    VisionErrorCode(HttpStatus status, String code, String message) {
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
