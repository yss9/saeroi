package com.ssafy.upcycle.product.dto;

import com.ssafy.upcycle.product.entity.ProductOrderStatus;

/**
 * 소비자 본인 주문 목록의 필터·페이지 조건.
 *
 * <p><b>구매자 id 는 받지 않는다.</b> JWT 의 사용자 id 로 강제해야 남의 주문을 조회할 수
 * 없다. 상태를 생략하면 모든 상태를 최신순으로 조회한다.
 */
public record ProductOrderSearchCondition(
        ProductOrderStatus status, Integer page, Integer size) {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    public int pageOrDefault() {
        return page == null || page < 0 ? 0 : page;
    }

    public int sizeOrDefault() {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
