package com.ssafy.upcycle.material.entity;

/**
 * 소재의 생애 주기.
 *
 * <p>{@link #DEPLETED} 로의 전환은 Processing·Production 확정으로 보유량이 모두 소진될 때
 * 일어난다. 해당 도메인이 들어올 때 전환 메서드를 추가한다.
 */
public enum MaterialStatus {

    ACTIVE,
    DEPLETED
}
