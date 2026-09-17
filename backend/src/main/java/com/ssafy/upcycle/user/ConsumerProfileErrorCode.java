package com.ssafy.upcycle.user;

import com.ssafy.upcycle.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 소비자 본인 개인정보 조회·수정 실패 사유.
 *
 * <p>{@code common} 은 도메인을 알면 안 되므로 도메인 에러 코드는 여기 둔다
 * ({@code CompanyErrorCode}·{@code ConsumerAddressErrorCode} 와 같은 모양).
 */
public enum ConsumerProfileErrorCode implements ErrorCode {

    /**
     * 소비자가 아닌 계정이 소비자 개인정보 경로를 쓰려는 경우.
     *
     * <p>사업자는 {@code /api/v1/companies/me} 로 같은 값(대표자명·업체 연락처)을 고친다.
     * 두 화면이 요구하는 연락처 형식이 서로 다르기 때문에 경로를 나눠 둔 것이다 — 소비자는
     * 휴대전화만 받고 사업자는 유선번호를 쓴다.
     *
     * <p>역할은 토큰 클레임이 아니라 DB 에서 다시 읽는다. 토큰은 발급 시점의 스냅샷이라
     * 역할이 바뀐 계정이 만료 전까지 계속 통과한다.
     */
    CONSUMER_PROFILE_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "CONSUMER_PROFILE_FORBIDDEN",
            "소비자 계정만 이용할 수 있습니다."),

    /**
     * 토큰은 유효한데 그 계정이 없는 경우.
     *
     * <p>정상적으로는 나오지 않는다. 계정을 지운 뒤 남은 토큰으로 들어오는 경우가 유일하다.
     */
    CONSUMER_PROFILE_NOT_FOUND(
            HttpStatus.NOT_FOUND, "CONSUMER_PROFILE_NOT_FOUND", "계정을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ConsumerProfileErrorCode(HttpStatus status, String code, String message) {
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
