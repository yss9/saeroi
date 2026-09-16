package com.ssafy.upcycle.user;

import com.ssafy.upcycle.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 업체 정보(company_profile) 조회·수정 실패 사유.
 *
 * <p>{@code common} 은 도메인을 알면 안 되므로 도메인 에러 코드는 여기 둔다
 * ({@code SignupErrorCode}·{@code WalletAuthErrorCode} 와 같은 모양).
 */
public enum CompanyErrorCode implements ErrorCode {
    COMPANY_PROFILE_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "COMPANY_PROFILE_FORBIDDEN",
            "업체 정보를 이용할 수 없는 계정입니다."),
    COMPANY_PROFILE_NOT_FOUND(
            HttpStatus.NOT_FOUND, "COMPANY_PROFILE_NOT_FOUND", "등록된 업체 정보를 찾을 수 없습니다."),
    /**
     * 소비자에게 공개할 브랜드가 아닌 경우.
     *
     * <p>없는 id 와 "판매 중인 공개 상품이 하나도 없는 브랜드" 를 같은 답으로 덮는다 —
     * 목록에 나오지 않는 브랜드의 상세만 열리면 빈 화면이 되고, 403 은 그 계정이 존재한다는
     * 사실을 알려 준다.
     */
    PUBLIC_BRAND_NOT_FOUND(
            HttpStatus.NOT_FOUND, "PUBLIC_BRAND_NOT_FOUND", "브랜드를 찾을 수 없습니다."),
    /** 로고를 올린 적이 없거나, 올린 파일이 저장소에서 사라진 경우. */
    BRAND_LOGO_NOT_FOUND(
            HttpStatus.NOT_FOUND, "BRAND_LOGO_NOT_FOUND", "브랜드 로고를 찾을 수 없습니다."),
    COMPANY_ADDRESS_NOT_FOUND(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "COMPANY_ADDRESS_NOT_FOUND",
            "입력한 주소의 위치를 찾을 수 없습니다."),
    COMPANY_GEOLOCATION_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "COMPANY_GEOLOCATION_UNAVAILABLE",
            "주소 위치 확인을 잠시 이용할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    CompanyErrorCode(HttpStatus status, String code, String message) {
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
