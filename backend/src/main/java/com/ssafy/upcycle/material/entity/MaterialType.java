package com.ssafy.upcycle.material.entity;

/**
 * 소재의 섬유 분류.
 *
 * <p>실제 취급하는 원단 섬유 7종({@link #COTTON}~{@link #WOOL})으로 좁히고, 그중 어디에도
 * 맞지 않는 소재가 갈 곳이 없어 등록 자체가 막히지 않도록 기타({@link #ETC})만 남겼다.
 *
 * <p>schema.sql 의 {@code material_type} 은 DB ENUM 이 아니라 {@code VARCHAR(50)} 이다.
 * 값을 추가해도 스키마 마이그레이션이 필요 없고 이 enum 만 고치면 된다.
 */
public enum MaterialType {

    COTTON,
    COTTON_MIXED,
    DENIM,
    POLYESTER,
    SILK,
    VISCOSE,
    WOOL,
    ETC
}
