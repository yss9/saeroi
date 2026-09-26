package com.ssafy.upcycle.user;

import com.ssafy.upcycle.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 비밀번호 변경 실패 사유. */
public enum PasswordChangeErrorCode implements ErrorCode {

    /**
     * 현재 비밀번호가 틀린 경우.
     *
     * <p><b>401 이 아니라 400 이다.</b> 로그인 실패({@code INVALID_CREDENTIALS})를 재사용하면
     * 프론트의 공통 인터셉터가 세션 만료로 해석해 로그아웃시킨다 — 비밀번호를 잘못 입력했을
     * 뿐인데 로그인 화면으로 튕기면 사용자는 무슨 일이 났는지 알 수 없다. 이미 인증을 통과한
     * 요청이고, 실패한 것은 본문에 담긴 입력값이다.
     */
    CURRENT_PASSWORD_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "CURRENT_PASSWORD_MISMATCH",
            "현재 비밀번호가 올바르지 않습니다."),

    /**
     * 새 비밀번호가 현재 비밀번호와 같은 경우.
     *
     * <p>막지 않으면 "변경되었습니다" 를 받고도 아무것도 바뀌지 않은 상태가 된다. 게다가 이
     * 경로는 세션을 전부 끊으므로, 바뀐 것 없이 다시 로그인만 하게 된다.
     *
     * <p>현재 비밀번호를 이미 맞춘 요청에만 도달하므로 이 응답이 무엇을 알려 주지는 않는다.
     */
    PASSWORD_SAME_AS_CURRENT(
            HttpStatus.BAD_REQUEST,
            "PASSWORD_SAME_AS_CURRENT",
            "새 비밀번호가 현재 비밀번호와 같습니다."),

    /** 토큰은 유효한데 그 계정이 없는 경우. 계정을 지운 뒤 남은 토큰으로 들어오는 경우다. */
    PASSWORD_CHANGE_ACCOUNT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "PASSWORD_CHANGE_ACCOUNT_NOT_FOUND",
            "계정을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    PasswordChangeErrorCode(HttpStatus status, String code, String message) {
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
