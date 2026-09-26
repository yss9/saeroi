package com.ssafy.upcycle.product.dto;

/**
 * 한 번에 주문할 상품 한 줄. 상품 하나와 그 수량이다.
 *
 * <p>장바구니 항목을 그대로 받지 않고 이 모양으로 옮겨 받는다 — 주문은 장바구니를 몰라야
 * 한다. 장바구니 없이도(예: 데이터 시드, 테스트) 같은 경로로 주문을 만들 수 있다.
 */
public record ProductOrderLine(Long productModelId, int quantity) {
}
