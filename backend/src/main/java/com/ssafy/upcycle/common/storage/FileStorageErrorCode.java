package com.ssafy.upcycle.common.storage;

import com.ssafy.upcycle.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 파일 저장 실패 사유. 정책 문서
 * {@code docs/superpowers/specs/2026-09-07-file-upload-policy.md} 의 Error Contract 와 1:1 이다.
 *
 * <p>{@code common} 에 두는 것이 맞다 — Material·Processing·Product·분쟁이 같은 코드를 받아야
 * 프론트가 도메인마다 다른 분기를 두지 않는다.
 *
 * <p>{@link #FILE_TYPE_MISMATCH} 를 {@link #FILE_TYPE_NOT_ALLOWED} 와 나눈 이유는 안내가
 * 달라야 해서다 — 하나는 "받지 않는 종류", 하나는 "이름이 내용과 다름" 이고,
 * {@code .heic} 를 {@code .jpg} 로 바꿔 올린 사용자는 후자를 들어야 한다.
 */
public enum FileStorageErrorCode implements ErrorCode {
    FILE_TYPE_NOT_ALLOWED(
            HttpStatus.BAD_REQUEST,
            "FILE_TYPE_NOT_ALLOWED",
            "허용되지 않는 파일 형식입니다. jpg · png · webp · pdf 만 올릴 수 있습니다."),
    FILE_TYPE_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "FILE_TYPE_MISMATCH",
            "파일 확장자와 실제 형식이 다릅니다."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "파일 하나는 10MB 를 넘을 수 없습니다."),
    FILE_COUNT_EXCEEDED(
            HttpStatus.BAD_REQUEST, "FILE_COUNT_EXCEEDED", "한 번에 10개까지 올릴 수 있습니다."),
    FILE_EMPTY(HttpStatus.BAD_REQUEST, "FILE_EMPTY", "빈 파일은 올릴 수 없습니다."),
    FILE_STORAGE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR, "FILE_STORAGE_FAILED", "파일 저장에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    FileStorageErrorCode(HttpStatus status, String code, String message) {
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
