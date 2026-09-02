package com.ssafy.upcycle.material.entity;

/**
 * 소재가 어디까지 검증되었는지. 등록 직후에는 {@link #REGISTERED} 이고,
 * 거래·가공·제품 연결을 거치며 단계가 올라간다.
 */
public enum MaterialVerificationState {

    REGISTERED,
    SOURCE_VERIFIED,
    TRANSACTION_VERIFIED,
    PROCESSING_VERIFIED,
    PRODUCT_LINKED
}
