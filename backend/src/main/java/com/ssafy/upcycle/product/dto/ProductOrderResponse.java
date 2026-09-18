package com.ssafy.upcycle.product.dto;

import com.ssafy.upcycle.product.entity.ProductOrderStatus;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 소비자가 보는 본인 주문. 결제 승인 키와 배송지 같은 민감 정보는 목록에 싣지 않는다.
 *
 * <p><b>{@code orderId} 와 {@code orderCode} 를 둘 다 싣는다.</b> 같은 주문을 가리키는 값이
 * 둘이라 중복처럼 보이지만 쓰임이 다르다 — {@code orderId} 는 결제·취소 경로가 받는 값이고
 * ({@code /product-orders/\{orderId\}/payment/ready}), {@code orderCode} 는 사람이 읽고 문의할 때
 * 대는 값이다. 코드만 내려보내면 주문을 만든 직후 결제를 이어갈 번호를 알 수 없다.
 */
public record ProductOrderResponse(
        Long orderId,
        String orderCode,

        /**
         * 이 주문이 속한 묶음 번호. <b>단일 주문은 {@code null} 이다.</b>
         *
         * <p>장바구니로 주문하면 브랜드 수만큼 주문이 생기고 결제는 묶음이 한 번 받는다.
         * 주문 목록에서 어느 주문들이 같은 결제였는지 알려면 이 값이 필요하다 — 없으면
         * 같은 순간에 생긴 주문 여러 건이 서로 무관해 보인다.
         *
         * <p>결제·취소 경로가 받는 값은 아니다. 묶음의 결제와 취소는
         * {@code orderGroupId} 를 받는 별도 경로가 처리한다.
         */
        String orderGroupCode,
        String brandName,
        long totalPrice,
        ProductOrderStatus status,
        LocalDateTime orderedAt,
        LocalDateTime paidAt,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt,
        List<ProductOrderItemResponse> items,

        /**
         * 송장. 발송 전에는 안의 값이 전부 {@code null} 이다.
         *
         * <p>구매자가 "배송 중" 만 보고 어디쯤 왔는지 알 수 없어 브랜드에 연락하던 것을
         * 메운다. 값은 원래 {@code product_shipping} 에 있었고 브랜드용 응답만 사용하고 있었다.
         */
        ProductOrderShippingResponse shipping,

        /**
         * 결제 마감 시각. <b>오프셋이 붙어 있다</b>({@code 2026-09-18T00:26:46+09:00}).
         *
         * <p>결제가 끝났거나 마감이 지난 주문도 값을 비우지 않는다 — 조건부로 {@code null}
         * 이면 화면이 "왜 없는지" 를 상태에서 다시 추론해야 한다. 지금 결제할 수 있는지는
         * {@code status} 가 말한다.
         *
         * <p>마감이 없는 주문은 {@code null} 이다.
         */
        OffsetDateTime paymentDeadline,

        /** 지금부터 마감까지 남은 초. 이미 지났으면 0, 마감이 없으면 {@code null}. */
        Long remainingSeconds) {
}
