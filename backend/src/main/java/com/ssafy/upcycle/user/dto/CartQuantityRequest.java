package com.ssafy.upcycle.user.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 장바구니 수량 변경 요청. <b>더하지 않고 그 값으로 지정한다.</b>
 *
 * <p>담기({@link CartAddRequest})와 나눈 이유가 이것이다. 하나로 합치면 화면이 현재 수량을
 * 알고 있어야 "담기" 를 할 수 있고, 목록을 열어 두지 않은 상세 화면에서는 그 값을 모른다.
 *
 * <p>{@code 0} 은 거절한다. 제거는 {@code DELETE} 가 따로 있고, 0 을 제거로 해석하면 실수로
 * 보낸 0 이 조용히 삭제를 일으킨다.
 */
public record CartQuantityRequest(
        @NotNull(message = "수량은 필수입니다.")
        Integer quantity) {
}
