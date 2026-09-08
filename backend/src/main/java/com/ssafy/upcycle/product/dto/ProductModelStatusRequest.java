package com.ssafy.upcycle.product.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 판매 상태 변경 요청.
 *
 * <p>{@code SOLD_OUT} 을 직접 지정할 수 없다. 품절은 브랜드가 정하는 값이 아니라
 * <b>개체를 세어 나오는 사실</b>이고, 판매·취소 처리가 알아서 전환한다. 손으로 바꿀 수 있게
 * 두면 재고가 있는데 품절이거나 그 반대인 상태가 만들어진다.
 */
public record ProductModelStatusRequest(

        @NotNull(message = "변경할 상태는 필수입니다.")
        Action action) {

    public enum Action {

        /** 판매 시작. 재고가 있어야 한다. */
        ON_SALE,

        /** 판매 중단. 재고와 무관하며 되돌릴 수 없다. */
        DISCONTINUE
    }
}
