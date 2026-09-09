package com.ssafy.upcycle.production.entity;

/**
 * {@code production_batch.status} ENUM 과 1:1 로 대응한다.
 *
 * <pre>
 *   DRAFT ──> SIGNING ──> CONFIRMED
 *     │          │
 *     │          └──> FAILED     (서명·전송 실패)
 *     └──> CANCELED              (확정 전에만)
 * </pre>
 *
 * <p><b>{@link #SIGNING} 이 따로 있는 이유.</b> 배치 확정은 소재를 소비하고 개체를 만들며
 * 온체인 기록까지 남기는 되돌릴 수 없는 행위다. 브랜드 지갑이 서명하는 동안 서버는
 * 기다리는데, 그 창을 상태로 남기지 않으면 <b>같은 배치를 두 번 확정</b>할 수 있다.
 * {@code DRAFT} 에서 바로 {@code CONFIRMED} 로 가는 설계는 그 중복을 막을 자리가 없다.
 *
 * <p>{@link #FAILED} 와 {@link #CANCELED} 를 구분하는 것도 같은 이유다 — 실패는 다시
 * 시도할 수 있고 취소는 사람이 그만둔 것이라, 화면이 보여줄 것과 다음 행동이 다르다.
 */
public enum ProductionBatchStatus {

    /** 작성 중. 투입 소재를 담고 수량을 맞추는 단계다. */
    DRAFT,

    /** 서명·전송 대기. 이 상태에서는 투입을 바꿀 수 없다. */
    SIGNING,

    /** 확정. 소재가 소비되고 개체가 만들어졌다. 되돌릴 수 없다. */
    CONFIRMED,

    /** 서명이나 전송이 실패했다. 잠금은 유지되고 다시 시도할 수 있다. */
    FAILED,

    /** 사람이 그만뒀다. 확정 전에만 가능하다. */
    CANCELED
}
