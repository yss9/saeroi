package com.ssafy.upcycle.user;

import com.ssafy.upcycle.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 지갑 서명 로그인의 실패 사유.
 *
 * <p>{@code common} 은 도메인을 알면 안 되므로 도메인 에러 코드는 여기 둔다
 * ({@code CommonErrorCode} 와 같은 모양, Lombok 없음).
 *
 * <p><b>{@code code} 는 기계 계약, {@code message} 는 사람이 읽는 문구다.</b>
 * Frontend 는 {@code code} 로 분기해 로그인 화면에 맞는 안내를 고르고, 매핑에 없는 코드는
 * 여기 적힌 {@code message} 를 그대로 보여준다. 따라서 코드 문자열을 바꾸면 Frontend 의
 * {@code LOGIN_ERROR_TEXT} 도 같이 바꿔야 한다.
 *
 * <p>{@link #NONCE_NOT_FOUND} 의 문구만 일부러 뭉뚱그렸다. "그런 nonce 없음" 을 그대로 알리면
 * 남의 nonce 를 넣어 보며 상태를 캐낼 여지를 주기 때문이다. 코드는 정확히 남긴다.
 */
public enum WalletAuthErrorCode implements ErrorCode {
    INVALID_WALLET_ADDRESS(
            HttpStatus.BAD_REQUEST, "INVALID_WALLET_ADDRESS", "지갑 주소 형식이 올바르지 않습니다."),
    INVALID_CHAIN_ID(HttpStatus.BAD_REQUEST, "INVALID_CHAIN_ID", "체인 ID 형식이 올바르지 않습니다."),
    WALLET_NOT_LINKED(HttpStatus.NOT_FOUND, "WALLET_NOT_LINKED", "등록되지 않은 지갑 주소입니다."),
    WALLET_CHAIN_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "WALLET_CHAIN_MISMATCH",
            "지갑에 연결된 네트워크가 등록된 네트워크와 다릅니다."),
    NONCE_NOT_FOUND(HttpStatus.BAD_REQUEST, "NONCE_NOT_FOUND", "유효하지 않은 인증 요청입니다."),
    NONCE_EXPIRED(
            HttpStatus.BAD_REQUEST, "NONCE_EXPIRED", "인증 요청이 만료되었습니다. 다시 시도해 주세요."),
    NONCE_ALREADY_USED(
            HttpStatus.BAD_REQUEST, "NONCE_ALREADY_USED", "이미 사용된 인증 요청입니다. 다시 시도해 주세요."),
    INVALID_SIGNATURE(HttpStatus.BAD_REQUEST, "INVALID_SIGNATURE", "서명 형식이 올바르지 않습니다."),
    SIGNATURE_MISMATCH(
            HttpStatus.UNAUTHORIZED, "SIGNATURE_MISMATCH", "서명이 지갑 주소와 일치하지 않습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    USER_SUSPENDED(HttpStatus.FORBIDDEN, "USER_SUSPENDED", "이용이 정지된 계정입니다."),
    WALLET_ROLE_NOT_ALLOWED(
            HttpStatus.FORBIDDEN,
            "WALLET_ROLE_NOT_ALLOWED",
            "지갑 로그인을 사용할 수 없는 계정입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    WalletAuthErrorCode(HttpStatus status, String code, String message) {
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
