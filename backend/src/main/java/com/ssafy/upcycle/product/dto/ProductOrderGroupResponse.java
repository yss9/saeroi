package com.ssafy.upcycle.product.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 한 번의 결제로 만들어진 주문 묶음.
 *
 * <p>브랜드 수만큼 주문이 들어 있다. 화면은 합계를 이 응답에서 읽고, 주문마다의 배송·상태는
 * 안쪽 {@link ProductOrderResponse} 에서 읽는다 — 브랜드가 따로 보내므로 배송은 주문마다다.
 *
 * <p><b>결제는 묶음 단위로 한 번</b>이다. 그래서 {@code totalPrice} 는 안쪽 주문 금액의 합이고
 * 결제창에 청구되는 금액이기도 하다.
 *
 * @param orderGroupCode 고객이 문의할 때 대는 묶음 번호 ({@code ORG-} 로 시작)
 */
public record ProductOrderGroupResponse(
        Long orderGroupId,
        String orderGroupCode,
        long totalPrice,
        LocalDateTime orderedAt,
        List<ProductOrderResponse> orders) {
}
