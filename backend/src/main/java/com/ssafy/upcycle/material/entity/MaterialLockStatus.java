package com.ssafy.upcycle.material.entity;

/**
 * 잠금의 생애 주기. 세 상태가 available 계산과 보유량에 각각 다르게 작용한다.
 *
 * <ul>
 *   <li>{@link #ACTIVE} — 잠금 중. available 에서 차감된다.</li>
 *   <li>{@link #RELEASED} — 취소·실패로 해제. 수량이 available 로 복귀한다.</li>
 *   <li>{@link #CONSUMED} — 실제 소비 완료. balance 의 quantity 자체가 줄어든다.</li>
 * </ul>
 */
public enum MaterialLockStatus {

    ACTIVE,
    RELEASED,
    CONSUMED
}
