package com.ssafy.upcycle.material.entity;

/**
 * Material Journey Timeline 에서 사건을 묶는 4단계 (S15P21E104-70).
 *
 * <p>{@link MaterialEventType} 은 40종에 가까워 화면에 그대로 나열하면 사용자가 흐름을
 * 읽을 수 없다. 이 값은 그 세부 이벤트를 "지금 이 소재가 어느 국면에 있는가" 로 묶는
 * 표시 전용 분류다 — 저장되지 않고 조회 시점에 {@link MaterialEventType} 에서 파생된다.
 */
public enum MaterialJourneyStage {
    /** 등록·발행·출처 검증·증빙. 아직 거래되지 않은 상태. */
    SOURCE,
    /** 판매 등록부터 소유권 이전까지. */
    TRANSACTION,
    /** 가공 요청부터 결과 확정까지. */
    PROCESSING,
    /** 생산 배치에 투입되어 제품으로 이어지는 지점. */
    PRODUCT_LINK
}
