package com.ssafy.upcycle.product.dto;

/**
 * 묶음 취소를 막고 있는 주문.
 *
 * <p>부분 취소가 없어 브랜드 한 곳이 발송하면 묶음 전체가 취소 불가가 된다. 사용자는
 * 브랜드별 발송 시점을 모르므로 <b>어느 주문이 나갔는지</b> 말해 주지 않으면 왜 취소가
 * 막혔는지 알 수 없다.
 *
 * @param brandName 사용자가 아는 이름. 브랜드 id 는 화면에 쓸 수 없다
 */
public record ShippedOrderLine(Long orderId, String orderCode, String brandName) {
}
