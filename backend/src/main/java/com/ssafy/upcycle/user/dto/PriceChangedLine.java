package com.ssafy.upcycle.user.dto;

/**
 * 담아 둔 뒤 값이 바뀐 항목.
 *
 * <p>{@code expectedTotalPrice} 를 보낸 요청이 거절될 때 함께 나간다. 합계만 알려 주면
 * 화면은 <b>어느 상품의 값이 바뀌었는지</b>를 다시 계산해야 한다.
 *
 * @param addedPrice 장바구니에 담을 때의 값
 * @param currentPrice 지금 값. 결제는 이 값으로 이뤄진다
 */
public record PriceChangedLine(
        Long productModelId, String productName, long addedPrice, long currentPrice) {
}
