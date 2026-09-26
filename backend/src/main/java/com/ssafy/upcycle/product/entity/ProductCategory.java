package com.ssafy.upcycle.product.entity;

import java.util.Arrays;
import java.util.List;

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

    /**
     * 위 어디에도 들어가지 않는 제품.
     *
     * <p><b>소비자 쇼핑몰에는 노출되지 않는다</b> ({@link #isConsumerVisible}). 값을 지우지
     * 않는 것은 이미 이 분류로 등록·판매된 제품이 있기 때문이다 — 브랜드 관리 화면과 주문
     * 이력에서는 그대로 보인다.
     */
    ETC;

    /**
     * 소비자 쇼핑몰(SHOP·검색·브랜드 상세)에 노출되는 분류인가.
     *
     * <p>목록에서 거르는 쪽과 상세를 404 로 막는 쪽이 같은 판단을 써야 한다. 두 벌로
     * 적으면 목록에서 안 보이는 제품의 상세가 id 만 알면 열리는 상태가 된다.
     */
    public boolean isConsumerVisible() {
        return this != ETC;
    }

    /**
     * 소비자에게 노출되는 분류 전부.
     *
     * <p>질의에서 {@code category in (...)} 으로 쓴다. {@code ETC} 하나를 {@code ne} 로 적지
     * 않는 이유는 감출 분류가 늘어도 질의가 그대로이기 때문이고, 상품 목록과 브랜드 목록이
     * <b>같은 목록</b>을 쓰게 하려는 것이다 — 두 곳이 따로 판단하면 브랜드 카드의 상품 수와
     * 실제로 그려지는 상품 수가 어긋난다.
     */
    public static List<ProductCategory> consumerVisible() {
        return Arrays.stream(values()).filter(ProductCategory::isConsumerVisible).toList();
    }
}
