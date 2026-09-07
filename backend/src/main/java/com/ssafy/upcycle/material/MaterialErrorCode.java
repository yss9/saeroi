package com.ssafy.upcycle.material;

import com.ssafy.upcycle.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 소재 등록·조회의 실패 사유.
 *
 * <p>{@code common} 은 도메인을 알면 안 되므로 도메인 에러 코드는 여기 둔다
 * ({@code WalletAuthErrorCode} 와 같은 모양, Lombok 없음).
 *
 * <p><b>{@code code} 는 기계 계약, {@code message} 는 사람이 읽는 문구다.</b>
 * Frontend 는 {@code code} 로 분기하므로 코드 문자열을 바꾸면 화면도 같이 바꿔야 한다.
 *
 * <p>Bean Validation 실패(필수 필드 누락, 형식 오류)는 {@code GlobalExceptionHandler} 가
 * 공통 봉투로 400 을 내주므로 여기에 코드를 따로 두지 않는다. 여기 있는 것은
 * <b>요청 형식은 맞지만 도메인 규칙에 걸리는</b> 경우뿐이다.
 */
public enum MaterialErrorCode implements ErrorCode {
    MATERIAL_NOT_FOUND(HttpStatus.NOT_FOUND, "MATERIAL_NOT_FOUND", "소재를 찾을 수 없습니다."),
    MATERIAL_REGISTRANT_NOT_FOUND(
            HttpStatus.NOT_FOUND, "MATERIAL_REGISTRANT_NOT_FOUND", "등록자를 찾을 수 없습니다."),
    MATERIAL_REGISTRANT_SUSPENDED(
            HttpStatus.FORBIDDEN, "MATERIAL_REGISTRANT_SUSPENDED", "이용이 정지된 계정입니다."),
    MATERIAL_ROLE_NOT_ALLOWED(
            HttpStatus.FORBIDDEN,
            "MATERIAL_ROLE_NOT_ALLOWED",
            "소재를 등록할 수 없는 계정입니다."),
    MATERIAL_QUANTITY_INVALID(
            HttpStatus.BAD_REQUEST, "MATERIAL_QUANTITY_INVALID", "수량이 올바르지 않습니다."),
    MATERIAL_LINEAGE_MULTIPLE_PARENTS(
            HttpStatus.CONFLICT,
            "MATERIAL_LINEAGE_MULTIPLE_PARENTS",
            "이 소재에는 이미 다른 부모 소재가 연결되어 있습니다."),
    MATERIAL_LINEAGE_DUPLICATE(
            HttpStatus.CONFLICT,
            "MATERIAL_LINEAGE_DUPLICATE",
            "같은 작업으로 이미 연결된 소재입니다."),
    MATERIAL_LINEAGE_ROOT_ORIGIN_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "MATERIAL_LINEAGE_ROOT_ORIGIN_MISMATCH",
            "자식 소재의 출처가 부모 소재와 다릅니다."),
    MATERIAL_COMPOSITION_DUPLICATE(
            HttpStatus.BAD_REQUEST,
            "MATERIAL_COMPOSITION_DUPLICATE",
            "같은 섬유 종류를 두 번 입력할 수 없습니다."),
    MATERIAL_COMPOSITION_TOTAL_EXCEEDED(
            HttpStatus.BAD_REQUEST,
            "MATERIAL_COMPOSITION_TOTAL_EXCEEDED",
            "구성 비율의 합은 100%를 넘을 수 없습니다."),
    MATERIAL_FIBER_TYPE_NOT_SUPPORTED(
            HttpStatus.BAD_REQUEST,
            "MATERIAL_FIBER_TYPE_NOT_SUPPORTED",
            "지원하지 않는 섬유 종류입니다."),
    MATERIAL_ORIGIN_REASON_INVALID(
            HttpStatus.BAD_REQUEST,
            "MATERIAL_ORIGIN_REASON_INVALID",
            "발생 사유가 소재 출처와 맞지 않습니다."),
    MATERIAL_UNIT_NOT_SUPPORTED(
            HttpStatus.BAD_REQUEST, "MATERIAL_UNIT_NOT_SUPPORTED", "지원하지 않는 단위입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    MaterialErrorCode(HttpStatus status, String code, String message) {
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
