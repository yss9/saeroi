package com.ssafy.upcycle.product.dto;

import com.ssafy.upcycle.product.entity.ProductOrderGroupStatus;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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
 * <p><b>취소는 묶음 전체다.</b> 브랜드 한 곳만 취소할 수는 없다 — 결제가 한 번이라 부분
 * 환불 금액을 나눌 근거가 없다. {@code cancelable} 이 화면의 취소 버튼과 확인 문구의
 * 근거이고, 묶음에 발송된 주문이 하나라도 생기면 {@code false} 가 된다.
 *
 * @param orderGroupCode 고객이 문의할 때 대는 묶음 번호 ({@code ORG-} 로 시작)
 * @param cancelable 지금 묶음 전체를 취소할 수 있는지
 */
public record ProductOrderGroupResponse(
        Long orderGroupId,
        String orderGroupCode,
        long totalPrice,
        ProductOrderGroupStatus status,
        LocalDateTime orderedAt,
        LocalDateTime paidAt,
        boolean cancelable,

        /** 결제 마감 시각. 오프셋이 붙는다 — 주문 응답과 같은 형식이다. */
        OffsetDateTime paymentDeadline,

        /** 지금부터 마감까지 남은 초. 이미 지났으면 0, 마감이 없으면 {@code null}. */
        Long remainingSeconds,
        List<ProductOrderResponse> orders) {
}
