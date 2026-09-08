package com.ssafy.upcycle.product.entity;

/**
 * 제품 분류.
 *
 * <p>schema.sql 의 {@code category} 는 DB ENUM 이 아니라 {@code VARCHAR(50)} 이다.
 * 값을 늘려도 스키마 마이그레이션이 필요 없고, 오타는 이 자바 enum 이 막는다 —
 * {@code MaterialType} 과 같은 구조다.
 */
public enum ProductCategory {

    BAG,
    WALLET,
    CLOTHING,
    ACCESSORY,
    HOME,
    ETC
}
