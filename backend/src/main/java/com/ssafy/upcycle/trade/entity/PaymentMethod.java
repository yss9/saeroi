package com.ssafy.upcycle.trade.entity;

/**
 * 결제 수단.
 *
 * <p>결제는 전적으로 오프체인이며 블록체인에 기록하지 않는다. 온체인에는 소유권 이전
 * 결과만 남으므로, 체인 기록만으로는 대금 지불 여부를 구분할 수 없다.
 *
 * <p>정책 v3 는 {@code KAKAOPAY} 한 종류로 정했으나 schema.sql 은
 * {@code ENUM('MOCK', 'KAKAO_PAY')} 로 되어 있다. 스키마를 기준으로 맞춘다 —
 * {@code MOCK} 은 결제 연동 전 시연·테스트 경로로 남아 있는 것으로 보인다.
 */
public enum PaymentMethod {

    MOCK,
    KAKAO_PAY
}
