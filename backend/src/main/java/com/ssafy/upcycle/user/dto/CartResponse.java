package com.ssafy.upcycle.user.dto;

import java.util.List;

/**
 * 장바구니 전체.
 *
 * <p>페이지를 나누지 않는다. 장바구니는 <b>합계를 보여 주는 화면</b>이라 어차피 전부
 * 필요하고, 페이지로 잘라 두면 총 금액을 어느 페이지 기준으로 낼지가 애매해진다. 응답
 * 크기는 항목 수 상한이 묶는다.
 *
 * <p>담기·수량 변경·제거도 이 응답을 돌려준다. 항목 하나를 바꾸면 합계가 함께 바뀌는데,
 * 그때마다 화면이 목록을 다시 불러야 하면 요청이 두 배가 된다.
 *
 * @param totalQuantity 지금 살 수 있는 항목들의 수량 합
 * @param totalPrice 지금 살 수 있는 항목들의 금액 합. <b>현재가 기준</b>이다 — 담은 시점
 *     가격으로 결제하지 않는다. 품절·단종 항목을 합계에 넣으면 화면의 합계와 실제로 결제될
 *     금액이 어긋나고, 그 차이를 화면이 다시 계산해야 한다. 어떤 항목이 빠졌는지는 항목별
 *     {@code purchasable} 로 알 수 있다
 */
public record CartResponse(
        List<CartItemResponse> items,
        int totalQuantity,
        long totalPrice) {
}
