package com.ssafy.upcycle.material.entity;

/**
 * 잠금을 유발한 도메인. {@code reference_id} 가 가리키는 테이블이 무엇인지 알려준다.
 *
 * <p>테이블마다 FK 를 따로 두지 않고 (type, id) 조합으로 다형 참조를 표현하므로,
 * DB 가 참조 무결성을 보장해주지 않는다. 실제 존재 여부는 잠금을 만드는 도메인이 책임진다.
 */
public enum MaterialLockReferenceType {

    LISTING,
    TRADE,
    PROCESSING_JOB,
    PRODUCTION_BATCH
}
