package com.ssafy.upcycle.trade.entity;

/**
 * 소재 거래의 상태. 2-Track 확정 구조를 그대로 따른다 — 보낸 측(Seller 배송)과
 * 받은 측(Buyer 수령 확인)이 각자 기록하고, 받은 측의 확인이 있어야 소유권이 넘어간다.
 *
 * <pre>
 *   CREATED → PAID → SHIPPED → RECEIVED → COMPLETED
 *                                  ↘ CANCELED
 * </pre>
 *
 * <p>{@link #SHIPPED} 동안에도 MARKETPLACE 잠금은 유지된다. 그러지 않으면 판매자가
 * 배송 중인 수량을 다른 곳에 또 팔 수 있다.
 *
 * <p>정책 v3 의 TradeStatus 는 {@code DISPUTED} 를 포함하지만 schema.sql 의 ENUM 에는
 * 아직 없다. 분쟁은 이번 범위 밖이라 스키마를 따른다.
 */
public enum TradeStatus {

    CREATED,
    PAID,
    SHIPPED,
    RECEIVED,
    COMPLETED,
    CANCELED
}
