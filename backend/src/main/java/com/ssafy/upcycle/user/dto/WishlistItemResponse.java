package com.ssafy.upcycle.user.dto;

import com.ssafy.upcycle.product.dto.PublicProductModelResponse;
import java.time.LocalDateTime;

/**
 * 찜 목록의 한 줄.
 *
 * <p><b>상품 부분이 SHOP 카드와 같은 모양이다.</b> 필드를 골라 새로 만들지 않고
 * {@link PublicProductModelResponse} 를 통째로 싣는다 — 화면이 SHOP 상품 카드를 그대로 쓰고,
 * 나중에 카드에 필드가 하나 늘어도 찜 목록이 따라서 바뀐다.
 *
 * <p><b>SHOP 목록과 다른 점은 무엇이 실리느냐다.</b> SHOP 은 공개 조건에 맞지 않는 상품을
 * 아예 빼지만, 찜은 사용자가 직접 담은 목록이라 단종·품절·감춘 분류로 바뀌어도 남는다.
 * 대신 그 사실을 화면이 알 수 있게 상태를 함께 내려보낸다 — 상품의 {@code status} 와
 * {@code stockQuantity} 로 "판매 종료" 와 "품절" 을 구분해 표시하면 된다.
 *
 * <p>{@code product.thumbnailUrl} 과 갤러리 주소는 <b>인증이 필요한 경로</b>다. 공개 썸네일은
 * 판매 중인 상품만 열려 있어서, 찜해 둔 상품이 단종되면 사진이 깨진다. 찜 목록 자체가
 * 로그인해야 보이는 화면이라 권한 수준이 정확히 맞는다 — 주문 내역이 같은 경로를 쓴다.
 *
 * @param purchasable 지금 살 수 있는가. <b>서버가 판단한다</b> — 판매 중이고, 재고가 있고,
 *     소비자에게 노출되는 분류여야 한다. 화면이 {@code status}·{@code stockQuantity}·
 *     {@code category} 로 이 규칙을 다시 만들면 판단하는 자리가 둘이 되고, 특히 감춘 분류로
 *     바뀐 상품은 화면이 알아낼 방법이 없다
 * @param wishedAt 담은 시각. 목록의 정렬 기준이라 화면이 "언제 담았는지" 를 보여줄 수 있다
 */
public record WishlistItemResponse(
        PublicProductModelResponse product,
        LocalDateTime wishedAt,
        boolean purchasable) {
}
