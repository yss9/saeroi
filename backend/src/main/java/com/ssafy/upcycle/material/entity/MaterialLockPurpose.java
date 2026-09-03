package com.ssafy.upcycle.material.entity;

/**
 * 무엇 때문에 수량을 잠갔는지.
 *
 * <p>{@link MaterialLockReferenceType} 과 짝을 이룬다. 용도가 잠금의 성격을 말한다면
 * reference 는 정확히 어느 건 때문인지를 가리킨다.
 */
public enum MaterialLockPurpose {

    MARKETPLACE,
    PROCESSING,
    PRODUCTION
}
