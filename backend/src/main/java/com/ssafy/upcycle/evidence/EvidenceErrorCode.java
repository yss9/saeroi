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
            HttpStatus.NOT_FOUND, "EVIDENCE_UPLOADER_NOT_FOUND", "업로더를 찾을 수 없습니다."),

    /**
     * 증빙 행이 없거나 이미 소프트 삭제됐다.
     *
     * <p>지워진 것과 없는 것을 <b>일부러 같은 답으로</b> 묶는다. 구분해 주면 "그 id 의 증빙은
     * 존재했다" 는 사실이 권한 없는 요청자에게 새어 나간다.
     */
    EVIDENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "증빙을 찾을 수 없습니다."),

    /**
     * 조회 권한이 없다.
     *
     * <p>증빙은 남의 소재 자료라 아무나 볼 수 없다. 볼 수 있는 것은 올린 사람, 대상 소재의
     * 등록자, 그 소재를 <b>실제로 보유한 사람</b>이다 — 거래 상대가 자기가 받은 소재의
     * 증빙을 확인하지 못하면 추적성이라는 제품의 목적 자체가 성립하지 않는다.
     */
    EVIDENCE_ACCESS_FORBIDDEN(
            HttpStatus.FORBIDDEN, "EVIDENCE_ACCESS_FORBIDDEN", "이 증빙을 볼 권한이 없습니다."),

    /**
     * 삭제 권한이 없다. <b>조회보다 좁다.</b>
     *
     * <p>보유자는 볼 수 있지만 지울 수 없다. 소재를 사 온 사람이 그 소재의 과거 증빙을
     * 지울 수 있으면 이력을 지우고 되파는 것이 가능해진다.
     */
    EVIDENCE_DELETE_FORBIDDEN(
            HttpStatus.FORBIDDEN, "EVIDENCE_DELETE_FORBIDDEN", "이 증빙을 지울 권한이 없습니다."),

    /**
     * 온체인에 고정된 증빙이라 지울 수 없다.
     *
     * <p>{@code 409} 다 — 요청이 틀린 것도({@code 400}) 권한 문제도({@code 403}) 아니다.
     * <b>대상의 현재 상태가 이 작업과 충돌</b>하는 것이고, 그 상태는 되돌아가지 않으므로
     * 사용자가 다시 시도할 일도 없다.
     */
    EVIDENCE_ANCHORED_ON_CHAIN(
            HttpStatus.CONFLICT,
            "EVIDENCE_ANCHORED_ON_CHAIN",
            "온체인에 기록된 증빙은 지울 수 없습니다.");

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
