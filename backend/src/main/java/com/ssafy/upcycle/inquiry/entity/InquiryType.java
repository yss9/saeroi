package com.ssafy.upcycle.inquiry.entity;

/**
 * 문의 유형. 프론트 문의하기 화면의 {@code TYPE_OPTIONS}와 값이 같아야 한다.
 *
 * <p>{@link #ETC}만 문의 대상 주문 없이 등록할 수 있다 — 나머지 세 유형은 특정 주문을
 * 짚어야 무엇을 확인해야 할지 알 수 있다.
 */
public enum InquiryType {
    DELIVERY,
    EXCHANGE_RETURN,
    PRODUCT,
    ETC;

    public boolean requiresOrder() {
        return this != ETC;
    }
}
