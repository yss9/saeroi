package com.ssafy.upcycle.material.entity;

/**
 * 소재의 물성 분류.
 *
 * <p>ERD 는 이 목록을 {@code DENIM / COTTON / LINING / METAL / ...} 로 열어둔 채 두었고,
 * 혼방({@link #MIXED})과 기타({@link #ETC})를 더해 닫았다. 분류가 애매한 소재가 갈 곳이 없으면
 * 등록 자체가 막히기 때문이다.
 *
 * <p>schema.sql 의 {@code material_type} 은 DB ENUM 이 아니라 {@code VARCHAR(50)} 이다.
 * 값을 추가해도 스키마 마이그레이션이 필요 없고 이 enum 만 고치면 된다.
 */
public enum MaterialType {

    DENIM,
    COTTON,
    LEATHER,
    LINING,
    METAL,
    MIXED,
    ETC
}
