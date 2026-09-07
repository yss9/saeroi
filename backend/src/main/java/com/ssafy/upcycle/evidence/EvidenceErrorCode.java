package com.ssafy.upcycle.evidence;

import com.ssafy.upcycle.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 증빙 업로드의 실패 사유.
 *
 * <p>파일 자체의 문제(확장자·크기·개수·빈 파일)는 {@code FileStorageErrorCode} 가 갖는다 —
 * 그건 저장소 계약이고 Product 이미지처럼 증빙이 아닌 용도도 같은 코드를 쓴다.
 * 여기 있는 것은 <b>증빙 도메인만의</b> 실패다.
 */
public enum EvidenceErrorCode implements ErrorCode {

    EVIDENCE_REFERENCE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "EVIDENCE_REFERENCE_NOT_FOUND",
            "증빙을 붙일 대상을 찾을 수 없습니다."),

    /**
     * 대상 종류는 유효하지만 아직 확인할 방법이 없는 경우.
     *
     * <p>{@code NOT_FOUND} 와 구분한다. 사용자에게 다른 뜻이기 때문이다 — "없는 대상" 은
     * 요청이 틀린 것이고, 이쪽은 <b>서버가 아직 못 하는 일</b>이다. 같은 코드로 묶으면
     * 사용자가 맞는 입력을 계속 고치게 된다.
     */
    EVIDENCE_REFERENCE_NOT_SUPPORTED(
            HttpStatus.BAD_REQUEST,
            "EVIDENCE_REFERENCE_NOT_SUPPORTED",
            "아직 이 대상에는 증빙을 붙일 수 없습니다."),

    EVIDENCE_FILE_MISSING(HttpStatus.BAD_REQUEST, "EVIDENCE_FILE_MISSING", "올릴 파일이 없습니다."),

    EVIDENCE_UPLOADER_NOT_FOUND(
            HttpStatus.NOT_FOUND, "EVIDENCE_UPLOADER_NOT_FOUND", "업로더를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    EvidenceErrorCode(HttpStatus status, String code, String message) {
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
