package com.ssafy.upcycle.prediction;

import com.ssafy.upcycle.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PredictionErrorCode implements ErrorCode {
    NOT_CONFIGURED(HttpStatus.NOT_IMPLEMENTED, "PREDICTION_NOT_CONFIGURED", "예측시장 온체인 연동이 설정되지 않았습니다."),
    MARKET_NOT_FOUND(HttpStatus.NOT_FOUND, "PREDICTION_MARKET_NOT_FOUND", "예측시장을 찾을 수 없습니다."),
    THUMBNAIL_NOT_FOUND(HttpStatus.NOT_FOUND, "PREDICTION_THUMBNAIL_NOT_FOUND", "예측시장 제품 사진이 없습니다."),
    MARKET_NOT_CANCELABLE(HttpStatus.CONFLICT, "PREDICTION_MARKET_NOT_CANCELABLE", "베팅이 진행 중인 시장만 무효화할 수 있습니다."),
    INVALID_MARKET(HttpStatus.BAD_REQUEST, "PREDICTION_MARKET_INVALID", "예측시장 조건이 올바르지 않습니다."),
    BRAND_INSIGHT_FORBIDDEN(HttpStatus.FORBIDDEN, "PREDICTION_BRAND_INSIGHT_FORBIDDEN", "승인된 브랜드만 예측 결과를 조회할 수 있습니다."),
    PARTICIPATION_FORBIDDEN(HttpStatus.FORBIDDEN, "PREDICTION_PARTICIPATION_FORBIDDEN", "소비자만 예측시장에 참여할 수 있습니다."),
    WALLET_REQUIRED(HttpStatus.CONFLICT, "PREDICTION_WALLET_REQUIRED", "MetaMask 지갑을 등록하고 소유 확인을 완료해 주세요."),
    RESPONSE_DUPLICATED(HttpStatus.CONFLICT, "PREDICTION_RESPONSE_DUPLICATED", "이미 이 시장에 예측 이유를 제출했습니다."),
    POINT_ALREADY_GRANTED(HttpStatus.CONFLICT, "PREDICTION_POINT_ALREADY_GRANTED", "예측 포인트가 이미 지급되었습니다."),
    // DB 는 확정인데 체인 기록(서명 네 번)이 중간에 멈춘 생산이다. 컨트랙트가 되돌리므로 보내기 전에 막는다.
    BATCH_NOT_CONFIRMED_ON_CHAIN(HttpStatus.CONFLICT, "PREDICTION_BATCH_NOT_CONFIRMED_ON_CHAIN", "이 생산은 블록체인 기록이 아직 끝나지 않아 예측시장을 열 수 없습니다. 브랜드가 생산 상세에서 남은 기록을 마쳐야 합니다."),
    CHAIN_TRANSACTION_FAILED(HttpStatus.BAD_GATEWAY, "PREDICTION_CHAIN_TRANSACTION_FAILED", "예측시장 온체인 처리에 실패했습니다.");

    private final HttpStatus status; private final String code; private final String message;
    PredictionErrorCode(HttpStatus status, String code, String message) { this.status=status; this.code=code; this.message=message; }
    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String message() { return message; }
}
