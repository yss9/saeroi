package com.ssafy.upcycle.trade;

import com.ssafy.upcycle.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 소재 거래 도메인 에러 코드 — 판매 등록·구매·결제·발송·수령.
 *
 * <p>{@code MaterialErrorCode} 와 같은 모양이다 — {@code common} 이 도메인을 알면 안 되므로
 * 도메인 에러 코드는 각 도메인 루트에 둔다.
 *
 * <p><b>{@code code} 는 기계 계약이다.</b> Frontend 가 이 문자열로 분기하므로
 * (예: 잔여 수량 부족이면 입력값을 되돌리고 목록을 다시 읽는다) 바꾸면 화면도 같이 바꿔야 한다.
 *
 * <p>여기 있는 것은 <b>요청 형식은 맞지만 도메인 규칙에 걸리는</b> 경우뿐이다.
 * 필수 필드 누락·형식 오류는 {@code GlobalExceptionHandler} 가 공통 봉투로 400 을 낸다.
 *
 * <p><b>같은 뜻인데 이름이 둘인 코드가 있다.</b> 판매·구매 흐름과 발송·수령 흐름이 서로 다른
 * 브랜치에서 만들어져 각자 이름을 붙였다 —
 * {@code LISTING_NOT_FOUND}/{@code TRADE_LISTING_NOT_FOUND},
 * {@code LISTING_LOCK_NOT_FOUND}/{@code TRADE_LOCK_NOT_FOUND} 가 그렇다.
 * 지금 한쪽으로 합치면 이미 그 코드로 분기하는 화면이 조용히 깨지므로 둘 다 둔다.
 * 정리는 프론트와 같은 주기에 한 번에 한다.
 */
public enum TradeErrorCode implements ErrorCode {

    // ── 공통 ────────────────────────────────────────────
    TRADE_NOT_FOUND(HttpStatus.NOT_FOUND, "TRADE_NOT_FOUND", "거래를 찾을 수 없습니다."),

    // ── 판매 등록 ────────────────────────────────────────
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

    // ── 구매 ────────────────────────────────────────────
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

    // ── 결제 ────────────────────────────────────────────
    PAYMENT_AMOUNT_MISMATCH(
            HttpStatus.BAD_REQUEST, "PAYMENT_AMOUNT_MISMATCH", "승인 금액이 거래 금액과 다릅니다."),
    PAYMENT_STATE_INVALID(
            HttpStatus.CONFLICT, "PAYMENT_STATE_INVALID", "지금 상태에서는 처리할 수 없습니다."),
    PAYMENT_NOT_PREPARED(
            HttpStatus.CONFLICT, "PAYMENT_NOT_PREPARED", "결제를 먼저 시작해야 합니다."),
    /**
     * 사용자가 결제창 인증을 마치지 않았다 (카카오 {@code -701}).
     *
     * <p><b>502 로 두면 안 된다.</b> 결제창을 닫거나 뒤로 가면 매번 나는 아주 흔한 경우인데
     * 502 는 "우리 인프라가 고장났다" 는 뜻이라, 오류 대시보드가 사용자 이탈로 가득 차
     * 진짜 장애를 덮는다. 사용자도 "결제 처리 중 오류" 만 보고는 무엇을 해야 할지 모른다
     * (2026-09-21 확인).
     *
     * <p>409 인 이유는 요청 자체는 멀쩡하고 <b>상태</b>가 아직 아니기 때문이다 —
     * {@code PAYMENT_NOT_PREPARED}·{@code PAYMENT_STATE_INVALID} 와 같은 부류다.
     */
    PAYMENT_NOT_AUTHENTICATED(
            HttpStatus.CONFLICT,
            "PAYMENT_NOT_AUTHENTICATED",
            "결제 인증이 완료되지 않았습니다. 결제를 다시 진행해 주세요."),
    PAYMENT_GATEWAY_FAILED(
            HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_FAILED", "결제 처리 중 오류가 발생했습니다."),

    // ── 발송 ────────────────────────────────────────────
    NOT_TRADE_SELLER(
            HttpStatus.FORBIDDEN, "NOT_TRADE_SELLER", "판매자만 처리할 수 있는 거래입니다."),
    TRADE_NOT_SHIPPABLE(
            HttpStatus.BAD_REQUEST, "TRADE_NOT_SHIPPABLE", "결제가 완료된 거래만 발송할 수 있습니다."),
    TRADE_ALREADY_SHIPPED(
            HttpStatus.CONFLICT, "TRADE_ALREADY_SHIPPED", "이미 발송 처리된 거래입니다."),
    TRADE_SHIPPING_NOT_FOUND(
            HttpStatus.NOT_FOUND, "TRADE_SHIPPING_NOT_FOUND", "배송지 정보가 없는 거래입니다."),
    TRADE_CARRIER_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "TRADE_CARRIER_REQUIRED",
            "운송장 번호를 입력하려면 택배사도 함께 입력해야 합니다."),

    // ── 수령 확인 ────────────────────────────────────────
    NOT_TRADE_BUYER(
            HttpStatus.FORBIDDEN, "NOT_TRADE_BUYER", "구매자만 처리할 수 있는 거래입니다."),
    TRADE_NOT_RECEIVABLE(
            HttpStatus.BAD_REQUEST, "TRADE_NOT_RECEIVABLE", "발송된 거래만 수령 확인할 수 있습니다."),
    TRADE_ALREADY_RECEIVED(
            HttpStatus.CONFLICT, "TRADE_ALREADY_RECEIVED", "이미 수령 확인된 거래입니다."),
    TRADE_LISTING_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "TRADE_LISTING_NOT_FOUND",
            "거래에 연결된 판매 등록을 찾을 수 없습니다."),
    TRADE_LOCK_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "TRADE_LOCK_NOT_FOUND",
            "거래에 연결된 소재 잠금을 찾을 수 없습니다."),
    TRADE_LOCK_NOT_ACTIVE(
            HttpStatus.CONFLICT, "TRADE_LOCK_NOT_ACTIVE", "이미 해제되었거나 소비된 잠금입니다."),
    TRADE_LOCK_AMOUNT_INSUFFICIENT(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "TRADE_LOCK_AMOUNT_INSUFFICIENT",
            "잠긴 수량이 거래 수량보다 적습니다."),
    SELLER_BALANCE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "SELLER_BALANCE_NOT_FOUND",
            "판매자의 소재 보유 내역을 찾을 수 없습니다."),
    SELLER_BALANCE_INSUFFICIENT(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "SELLER_BALANCE_INSUFFICIENT",
            "판매자의 보유 수량이 거래 수량보다 적습니다.");

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
