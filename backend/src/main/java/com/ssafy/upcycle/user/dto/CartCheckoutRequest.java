package com.ssafy.upcycle.user.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 장바구니에서 고른 항목으로 주문한다.
 *
 * <p><b>담긴 전부가 아니라 고른 것만 주문한다.</b> 장바구니는 단종·품절·감춘 분류로 바뀐
 * 항목도 일부러 남겨 두므로, 전부를 주문하면 그 항목들 때문에 늘 실패하게 된다. 화면이
 * 체크박스로 고르는 것과도 맞다.
 *
 * <p>수량은 받지 않는다. 장바구니에 담긴 수량을 그대로 쓴다 — 여기서 수량을 받으면 화면이
 * 보여 준 장바구니와 실제로 주문된 수량이 어긋날 수 있고, 수량 변경 경로는 따로 있다.
 *
 * <p>가격도 받지 않는다. 금액은 <b>주문 시점의 현재가</b>로 서버가 계산한다.
 *
 * @param productModelIds 주문할 상품. 같은 id 가 두 번 오면 한 번으로 친다
 * @param expectedTotalPrice 화면이 보여 준 합계. <b>선택이다</b> — 보내면 서버 계산 금액과
 *     대조해 다르면 거절하고, 보내지 않으면 현재가로 그냥 진행한다. 담아 둔 사이에 값이
 *     오른 것을 모르고 결제하는 경로를 막는 안전장치다
 */
public record CartCheckoutRequest(

        @NotEmpty(message = "주문할 상품을 선택해 주세요.")
        @Size(max = 50, message = "한 번에 50종까지 주문할 수 있습니다.")
        List<@NotNull(message = "상품을 선택해 주세요.")
                @Positive(message = "상품 id는 양수여야 합니다.") Long> productModelIds,

        @NotNull(message = "배송지를 선택해 주세요.")
        @Positive(message = "배송지 id는 양수여야 합니다.")
        Long addressId,

        @PositiveOrZero(message = "예상 결제 금액은 0원 이상이어야 합니다.")
        Long expectedTotalPrice) {
}
