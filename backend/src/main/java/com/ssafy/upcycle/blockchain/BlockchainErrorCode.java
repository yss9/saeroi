package com.ssafy.upcycle.blockchain;

import com.ssafy.upcycle.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 온체인 기록의 실패 사유. */
public enum BlockchainErrorCode implements ErrorCode {
    /**
     * 컨트랙트 주소가 설정되지 않았다.
     *
     * <p><b>501 인 것이 의도적이다.</b> 사용자가 잘못한 것이 아니라 서버가 그 기능을 아직
     * 갖추지 않은 상태다. 400 으로 내려보내면 화면이 "입력이 잘못됐다" 로 안내하게 된다.
     */
    BLOCKCHAIN_NOT_CONFIGURED(
            HttpStatus.NOT_IMPLEMENTED,
            "BLOCKCHAIN_NOT_CONFIGURED",
            "온체인 연동이 설정되지 않았습니다."),
    BLOCKCHAIN_ALREADY_LINKED(
            HttpStatus.CONFLICT,
            "BLOCKCHAIN_ALREADY_LINKED",
            "이미 온체인에 등록된 대상입니다."),
    BLOCKCHAIN_LINK_INVALID(
            HttpStatus.BAD_REQUEST,
            "BLOCKCHAIN_LINK_INVALID",
            "온체인 기록 정보가 올바르지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    BlockchainErrorCode(HttpStatus status, String code, String message) {
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
