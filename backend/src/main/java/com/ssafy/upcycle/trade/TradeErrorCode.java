package com.ssafy.upcycle.trade;

import com.ssafy.upcycle.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 판매 등록·구매·결제의 실패 사유.
 *
 * <p>{@code MaterialErrorCode} 와 같은 모양이다 — {@code common} 이 도메인을 알면 안 되므로
 * 도메인 에러 코드는 각 도메인 루트에 둔다.
 *
 * <p><b>{@code code} 는 기계 계약이다.</b> Frontend 가 이 문자열로 분기하므로
 * (예: 잔여 수량 부족이면 입력값을 되돌리고 목록을 다시 읽는다) 바꾸면 화면도 같이 바꿔야 한다.
 *
 * <p>여기 있는 것은 <b>요청 형식은 맞지만 도메인 규칙에 걸리는</b> 경우뿐이다.
 * 필수 필드 누락·형식 오류는 {@code GlobalExceptionHandler} 가 공통 봉투로 400 을 낸다.
 */
public enum TradeErrorCode implements ErrorCode {

    // --- 판매 등록 ---
    LISTING_NOT_FOUND(HttpStatus.NOT_FOUND, "LISTING_NOT_FOUND", "판매 글을 찾을 수 없습니다."),
    LISTING_SELLER_NOT_FOUND(
            HttpStatus.NOT_FOUND, "LISTING_SELLER_NOT_FOUND", "판매자를 찾을 수 없습니다."),
    LISTING_SELLER_SUSPENDED(
            HttpStatus.FORBIDDEN, "LISTING_SELLER_SUSPENDED", "이용이 정지된 계정입니다."),
    LISTING_ROLE_NOT_ALLOWED(
            HttpStatus.FORBIDDEN, "LISTING_ROLE_NOT_ALLOWED", "소재를 판매할 수 없는 계정입니다."),
    LISTING_NOT_OWNED(
            HttpStatus.FORBIDDEN, "LISTING_NOT_OWNED", "본인의 판매 글만 다룰 수 있습니다."),
    LISTING_MATERIAL_NOT_FOUND(
            HttpStatus.NOT_FOUND, "LISTING_MATERIAL_NOT_FOUND", "소재를 찾을 수 없습니다."),
    LISTING_MATERIAL_NOT_OWNED(
            HttpStatus.FORBIDDEN, "LISTING_MATERIAL_NOT_OWNED", "보유하지 않은 소재입니다."),
    LISTING_QUANTITY_INVALID(
            HttpStatus.BAD_REQUEST, "LISTING_QUANTITY_INVALID", "수량이 올바르지 않습니다."),
    LISTING_QUANTITY_EXCEEDS_AVAILABLE(
            HttpStatus.CONFLICT,
            "LISTING_QUANTITY_EXCEEDS_AVAILABLE",
            "판매 가능 수량을 초과했습니다."),
    LISTING_NOT_ON_SALE(
            HttpStatus.CONFLICT, "LISTING_NOT_ON_SALE", "판매 중인 글이 아닙니다."),
    LISTING_HAS_ONGOING_TRADE(
            HttpStatus.CONFLICT,
            "LISTING_HAS_ONGOING_TRADE",
            "진행 중인 거래가 있어 취소할 수 없습니다."),
    LISTING_LOCK_NOT_FOUND(
            HttpStatus.INTERNAL_SERVER_ERROR, "LISTING_LOCK_NOT_FOUND", "판매 잠금을 찾을 수 없습니다."),

    // --- 거래 ---
    TRADE_NOT_FOUND(HttpStatus.NOT_FOUND, "TRADE_NOT_FOUND", "거래를 찾을 수 없습니다."),
    TRADE_BUYER_NOT_FOUND(HttpStatus.NOT_FOUND, "TRADE_BUYER_NOT_FOUND", "구매자를 찾을 수 없습니다."),
    TRADE_BUYER_SUSPENDED(
            HttpStatus.FORBIDDEN, "TRADE_BUYER_SUSPENDED", "이용이 정지된 계정입니다."),
    TRADE_ROLE_NOT_ALLOWED(
            HttpStatus.FORBIDDEN, "TRADE_ROLE_NOT_ALLOWED", "소재를 구매할 수 없는 계정입니다."),
    TRADE_SELF_PURCHASE(
            HttpStatus.BAD_REQUEST, "TRADE_SELF_PURCHASE", "자신의 판매 글은 구매할 수 없습니다."),
    TRADE_NOT_PARTICIPANT(
            HttpStatus.FORBIDDEN, "TRADE_NOT_PARTICIPANT", "이 거래의 당사자가 아닙니다."),
    TRADE_WALLET_NOT_LINKED(
            HttpStatus.CONFLICT, "TRADE_WALLET_NOT_LINKED", "지갑이 연결되어 있지 않습니다."),
    TRADE_PRICE_OVERFLOW(
            HttpStatus.BAD_REQUEST, "TRADE_PRICE_OVERFLOW", "거래 금액을 계산할 수 없습니다."),

    // --- 결제 ---
    PAYMENT_AMOUNT_MISMATCH(
            HttpStatus.BAD_REQUEST, "PAYMENT_AMOUNT_MISMATCH", "승인 금액이 거래 금액과 다릅니다."),
    PAYMENT_STATE_INVALID(
            HttpStatus.CONFLICT, "PAYMENT_STATE_INVALID", "지금 상태에서는 처리할 수 없습니다.");

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
