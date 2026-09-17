package com.ssafy.upcycle.user.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 장바구니 담기 요청. <b>수량을 더한다</b> — 이미 담은 상품이면 기존 수량에 누적된다.
 *
 * <p>사용자 id 는 받지 않는다. JWT {@code sub} 에서 꺼낸다 — 바디로 받으면 남의 장바구니에
 * 상품을 넣을 수 있다.
 *
 * <p>수량 범위는 여기서 보지 않는다. 0 과 음수, 상한 초과를 한 자리에서 같은 모양의 오류로
 * 답해야 화면이 한 곳만 보면 되기 때문이다 ({@code CART_QUANTITY_OUT_OF_RANGE}).
 */
public record CartAddRequest(
        @NotNull(message = "상품 id는 필수입니다.")
        Long productModelId,

        @NotNull(message = "수량은 필수입니다.")
        Integer quantity) {
}
