package com.ssafy.upcycle.user.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 찜 추가 요청.
 *
 * <p>사용자 id 는 받지 않는다 — JWT {@code sub} 에서 꺼낸다. 바디로 받으면 남의 찜 목록에
 * 상품을 넣을 수 있다.
 */
public record WishlistAddRequest(
        @NotNull(message = "상품 id는 필수입니다.")
        Long productModelId) {
}
