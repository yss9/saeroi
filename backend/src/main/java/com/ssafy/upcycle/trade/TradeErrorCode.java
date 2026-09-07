package com.ssafy.upcycle.trade;

import com.ssafy.upcycle.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 소재 거래 도메인 에러 코드. */
public enum TradeErrorCode implements ErrorCode {

    TRADE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "TRADE_NOT_FOUND",
            "거래를 찾을 수 없습니다."),

    NOT_TRADE_SELLER(
            HttpStatus.FORBIDDEN,
            "NOT_TRADE_SELLER",
            "판매자만 처리할 수 있는 거래입니다."),

    TRADE_NOT_SHIPPABLE(
            HttpStatus.BAD_REQUEST,
            "TRADE_NOT_SHIPPABLE",
            "결제가 완료된 거래만 발송할 수 있습니다."),

    TRADE_ALREADY_SHIPPED(
            HttpStatus.CONFLICT,
            "TRADE_ALREADY_SHIPPED",
            "이미 발송 처리된 거래입니다."),

    TRADE_SHIPPING_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "TRADE_SHIPPING_NOT_FOUND",
            "배송지 정보가 없는 거래입니다."),

    TRADE_CARRIER_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "TRADE_CARRIER_REQUIRED",
            "운송장 번호를 입력하려면 택배사도 함께 입력해야 합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    TradeErrorCode(HttpStatus status, String code, String message) {
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
