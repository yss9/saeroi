package com.ssafy.upcycle.product.dto;

/**
 * 재고가 모자라 주문이 거절된 상품 한 줄.
 *
 * <p>여러 상품을 한 번에 주문할 때 <b>하나라도 부족하면 전부 실패</b>시키는데, 그때 어떤
 * 상품이 문제인지 알려주지 않으면 사용자는 장바구니에서 무엇을 빼야 할지 알 수 없다.
 * 에러 응답의 {@code data} 에 실려 나간다.
 *
 * @param available 지금 남은 수량. {@code 0} 이면 품절이다
 */
public record OutOfStockLine(
        Long productModelId,
        String productName,
        int requested,
        long available) {
}
