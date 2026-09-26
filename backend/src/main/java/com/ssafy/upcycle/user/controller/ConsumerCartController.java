package com.ssafy.upcycle.user.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.product.dto.ProductOrderGroupResponse;
import com.ssafy.upcycle.user.dto.CartAddRequest;
import com.ssafy.upcycle.user.dto.CartCheckoutRequest;
import com.ssafy.upcycle.user.dto.CartQuantityRequest;
import com.ssafy.upcycle.user.dto.CartResponse;
import com.ssafy.upcycle.user.service.ConsumerCartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소비자 본인의 장바구니. <b>인증이 필요하고 {@code CONSUMER} 만 쓸 수 있다.</b>
 *
 * <p>경로에 사용자 id 가 없다 — 사용자는 JWT {@code sub} 에서 얻는다. 배송지·찜이 쓰는
 * "내 리소스" 관례를 그대로 따르며, <b>남의 장바구니를 가리키는 요청 자체가 만들어지지
 * 않는다.</b>
 *
 * <p>항목을 <b>장바구니 행 id 가 아니라 상품 id 로</b> 가리킨다. 화면이 보고 있는 것은
 * 상품이고, 그 상품의 장바구니 행 id 를 따로 들고 있을 이유가 없다 — 찜과 같다.
 *
 * <p><b>주문은 고른 항목만 만든다</b>({@code POST /checkout}). 결제는 그 다음 단계이고
 * 묶음 단위로 {@code /api/v1/product-order-groups} 가 받는다.
 */
@RestController
@RequestMapping("/api/v1/consumers/me/cart")
@RequiredArgsConstructor
public class ConsumerCartController {

    private final ConsumerCartService consumerCartService;

    /**
     * 내 장바구니.
     *
     * <p><b>페이지를 나누지 않는다.</b> 합계를 보여 주는 화면이라 어차피 전부 필요하고,
     * 응답 크기는 담을 수 있는 상품 종류 수가 묶는다.
     *
     * <p><b>SHOP 목록과 담는 기준이 다르다.</b> 판매가 끝났거나 품절이거나 소비자에게 감추는
     * 분류로 바뀐 상품도 그대로 남는다 — 사용자가 직접 담은 목록이라 서버가 지우지 않는다.
     * 지금 살 수 있는지는 항목별 {@code purchasable} 로, 왜 못 사는지는 상품의
     * {@code status} 와 {@code stockQuantity} 로 알 수 있다.
     *
     * <p>브랜드로 묶지 않은 평면 목록이다. 카드에 {@code brandId}·{@code brandName} 이 이미
     * 있어 화면이 묶어 그릴 수 있고, 브랜드별 주문 분리는 아직 정해지지 않은 정책이다.
     */
    @GetMapping
    public ApiResponse<CartResponse> findMine(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(consumerCartService.findMine(userId(jwt)));
    }

    /**
     * 담는다. <b>이미 담긴 상품이면 수량을 더한다.</b>
     *
     * <p>상세 화면에서 "장바구니 담기" 를 두 번 누르면 2개가 된다. 남은 재고보다 많이 담을
     * 수는 없지만, <b>그 검사가 재고를 확보해 주지는 않는다</b> — 담기는 예약이 아니고 실제
     * 확보는 주문 시점이다.
     */
    @PostMapping
    public ApiResponse<CartResponse> add(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CartAddRequest request) {
        return ApiResponse.success(consumerCartService.add(
                userId(jwt), request.productModelId(), request.quantity()));
    }

    /**
     * 수량을 바꾼다. <b>더하지 않고 보낸 값으로 지정한다.</b>
     *
     * <p>담기와 나눈 이유는 상세 화면이 현재 수량을 모르기 때문이다. {@code 0} 은 거절한다 —
     * 제거는 아래 경로가 따로 있고, 0 을 제거로 해석하면 실수로 보낸 0 이 조용히 삭제를
     * 일으킨다.
     */
    @PatchMapping("/{productModelId}")
    public ApiResponse<CartResponse> changeQuantity(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long productModelId,
            @Valid @RequestBody CartQuantityRequest request) {
        return ApiResponse.success(consumerCartService.changeQuantity(
                userId(jwt), productModelId, request.quantity()));
    }

    /** 항목을 뺀다. <b>담겨 있지 않아도 성공이다</b> — 결과 상태가 같다. */
    @DeleteMapping("/{productModelId}")
    public ApiResponse<CartResponse> remove(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long productModelId) {
        return ApiResponse.success(consumerCartService.remove(userId(jwt), productModelId));
    }

    /**
     * 고른 항목으로 주문한다. <b>결제는 아직이고 장바구니도 아직 비우지 않는다.</b>
     *
     * <p>담긴 전부가 아니라 <b>고른 것만</b> 주문한다. 목록이 단종·품절·감춘 분류로 바뀐
     * 항목도 남겨 두기 때문에 전부를 주문하면 늘 실패한다.
     *
     * <p>브랜드가 둘 이상이면 주문은 브랜드 수만큼 생기고 응답의 묶음이 그것을 덮는다.
     * 다음 호출은 {@code POST /api/v1/product-order-groups/\{orderGroupId\}/payment/ready}
     * 이고, 장바구니는 승인이 끝난 뒤 <b>주문에 들어간 항목만</b> 비워진다.
     *
     * <p>살 수 없는 항목이 섞이면 <b>전부 거절한다</b> — 되는 것만 주문하면 화면이 보여 준
     * 합계와 다른 금액이 결제된다. 어떤 상품이 왜 안 되는지는 응답 상세에 실린다.
     */
    @PostMapping("/checkout")
    public ApiResponse<ProductOrderGroupResponse> checkout(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CartCheckoutRequest request) {
        return ApiResponse.success(consumerCartService.checkout(userId(jwt), request));
    }

    /**
     * 전부 비운다.
     *
     * <p>항목마다 요청을 보내면 중간에 끊겼을 때 일부만 지워진 장바구니가 남는다.
     */
    @DeleteMapping
    public ApiResponse<CartResponse> clear(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(consumerCartService.clear(userId(jwt)));
    }

    /**
     * JWT {@code sub} 는 {@code JwtTokenService} 가 {@code String.valueOf(user.getId())} 로
     * 넣는다. 형식이 깨진 토큰은 여기 오기 전에 {@code JwtDecoder} 가 거부한다.
     */
    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
