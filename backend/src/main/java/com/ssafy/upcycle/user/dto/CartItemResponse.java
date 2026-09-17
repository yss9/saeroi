package com.ssafy.upcycle.user.dto;

import com.ssafy.upcycle.product.dto.PublicProductModelResponse;
import java.time.LocalDateTime;

/**
 * 장바구니의 한 줄.
 *
 * <p><b>상품 부분이 SHOP 카드와 같은 모양이다.</b> 필드를 골라 새로 만들지 않고
 * {@link PublicProductModelResponse} 를 통째로 싣는다 — 화면이 상품 카드를 그대로 쓰고,
 * 카드에 필드가 하나 늘어도 장바구니가 따라서 바뀐다. 찜과 같은 판단이다.
 *
 * <p>브랜드로 묶지 않고 평면으로 준다. 카드 안에 {@code brandId}·{@code brandName} 이 이미
 * 있어 화면이 묶어 보여줄 수 있고, 브랜드별 주문 분리는 아직 정해지지 않은 정책이라 응답
 * 구조로 굳히지 않는다.
 *
 * <p>{@code product.stockQuantity} 로 "3개 남음" 을 그릴 수 있다. 담을 때의 재고 검사는
 * 최종 보증이 아니므로({@code ConsumerCartService} 주석) 화면도 현재 재고를 보여 주는 편이
 * 정직하다.
 *
 * <p>{@code product.thumbnailUrl} 과 갤러리 주소는 <b>인증이 필요한 경로</b>다. 공개
 * 썸네일은 판매 중인 상품만 열려 있어서, 담아 둔 상품이 단종되면 사진이 깨진다. 장바구니는
 * 로그인해야 보이는 화면이라 권한 수준이 정확히 맞는다 — 찜·주문 내역과 같은 경로다.
 *
 * @param addedPrice 이 항목을 마지막으로 담을 때 사용자가 본 가격. <b>결제 기준이 아니다</b>
 * @param priceChanged 담은 뒤 값이 바뀌었는가. 두 값을 함께 주므로 화면이 "89,000 → 99,000"
 *     을 그리고 올랐는지 내렸는지도 비교로 알 수 있다. 같고 다름의 기준을 서버가 한 번만
 *     정해 두려고 이 플래그를 함께 둔다
 * @param purchasable 지금 살 수 있는가 — 판매 중이고, 재고가 있고, 소비자에게 노출되는
 *     분류여야 한다. 찜과 같은 규칙이고, 화면이 이 규칙을 다시 만들면 판단하는 자리가 둘이
 *     된다
 * @param addedAt 처음 담은 시각. 목록 순서는 담은 순서이지만 시각 자체도 화면이 쓸 수 있다
 */
public record CartItemResponse(
        PublicProductModelResponse product,
        int quantity,
        long addedPrice,
        boolean priceChanged,
        boolean purchasable,
        LocalDateTime addedAt) {
}
