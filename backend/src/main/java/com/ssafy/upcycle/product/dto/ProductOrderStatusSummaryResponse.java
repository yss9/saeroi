package com.ssafy.upcycle.product.dto;

/** 소비자 마이페이지의 주문 진행 현황에 사용할 상태별 주문 수. */
public record ProductOrderStatusSummaryResponse(
        long created,
        long paid,
        long shipped,
        long delivered) {
}
