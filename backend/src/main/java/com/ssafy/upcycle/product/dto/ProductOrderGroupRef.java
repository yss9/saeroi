package com.ssafy.upcycle.product.dto;

/**
 * 이 주문이 속한 묶음. <b>단일 주문 경로가 거절될 때 갈 곳을 알려 준다.</b>
 *
 * <p>거절된 화면이 할 일은 {@code /api/v1/product-order-groups/\{orderGroupId\}} 쪽으로
 * 옮겨 가는 것 하나뿐인데, id 가 없으면 주문 목록을 다시 불러 그 주문이 어느 묶음에
 * 속하는지 찾아야 한다.
 *
 * @param orderGroupCode 사용자에게 보여 줄 묶음 번호 ({@code ORG-} 로 시작)
 */
public record ProductOrderGroupRef(Long orderGroupId, String orderGroupCode) {
}
