package com.ssafy.upcycle.product.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 제품 주문 생성 요청. {@code POST /api/v1/product-orders}.
 *
 * <p><b>수량이 없다.</b> 제품 개체는 한 점씩 만들어지는 물건이라 같은 개체를 두 개 살 수
 * 없다. 한 주문은 개체 하나다.
 *
 * <p><b>개체 id 도 받지 않는다.</b> 화면이 개체를 지목하면 그 사이 남이 사 간 개체를
 * 가리키게 된다. 어느 개체를 팔지는 서버가 주문 시점에 재고에서 고른다.
 */
public record ProductOrderCreateRequest(

        @NotNull(message = "제품을 선택해 주세요.")
        @Positive(message = "제품 id는 양수여야 합니다.")
        Long productModelId,

        /** {@code GET /api/v1/consumers/me/addresses} 가 답한 배송지의 id. */
        @NotNull(message = "배송지를 선택해 주세요.")
        @Positive(message = "배송지 id는 양수여야 합니다.")
        Long addressId) {
}
