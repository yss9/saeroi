package com.ssafy.upcycle.user.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.trade.dto.PageResponse;
import com.ssafy.upcycle.user.dto.WishlistAddRequest;
import com.ssafy.upcycle.user.dto.WishlistItemResponse;
import com.ssafy.upcycle.user.service.ConsumerWishlistService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소비자 본인의 찜. <b>인증이 필요하고 {@code CONSUMER} 만 쓸 수 있다.</b>
 *
 * <p>경로에 사용자 id 가 없다 — 사용자는 JWT {@code sub} 에서 얻는다. 배송지
 * ({@code /api/v1/consumers/me/addresses})가 쓰는 "내 리소스" 관례를 그대로 따르며,
 * <b>남의 찜을 가리키는 요청 자체가 만들어지지 않는다.</b>
 *
 * <p>비로그인 사용자의 찜은 서버로 옮기지 않는다. 지금처럼 로그인으로 보내고 원래 상품으로
 * 돌아오는 흐름을 유지한다.
 */
@RestController
@RequestMapping("/api/v1/consumers/me/wishlist")
@RequiredArgsConstructor
public class ConsumerWishlistController {

    private final ConsumerWishlistService consumerWishlistService;

    /**
     * 내 찜 목록. <b>최근에 담은 것부터</b>이고 기본 12개다.
     *
     * <p><b>SHOP 목록과 담는 기준이 다르다.</b> 판매가 끝났거나 품절이거나 소비자에게 감추는
     * 분류로 바뀐 상품도 그대로 남는다 — 사용자가 직접 담은 목록이라 서버가 지우지 않는다.
     * 대신 지금 살 수 있는지를 {@code purchasable} 로, 왜 못 사는지를 상품의 {@code status}
     * 와 {@code stockQuantity} 로 알 수 있다.
     */
    @GetMapping
    public ApiResponse<PageResponse<WishlistItemResponse>> findMine(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.success(consumerWishlistService.findMine(userId(jwt), page, size));
    }

    /**
     * 찜에 담는다. <b>이미 담긴 상품을 다시 보내도 성공이다.</b>
     *
     * <p>하트 버튼은 연타되고 네트워크 재시도도 흔하다. 결과 상태가 같으므로 같은 답을 준다.
     *
     * <p>품절이나 판매 종료 상품도 담을 수 있다 — 다시 들어오기를 기다리며 담아 두는 것이
     * 찜의 쓰임 중 하나다.
     */
    @PostMapping
    public ApiResponse<Void> add(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody WishlistAddRequest request) {
        consumerWishlistService.add(userId(jwt), request.productModelId());
        return ApiResponse.success(null);
    }

    /**
     * 찜을 뺀다. <b>담겨 있지 않아도 성공이다.</b>
     *
     * <p>찜 행의 id 가 아니라 상품 id 로 받는다 — 화면이 보고 있는 것은 상품이고, 그 상품의
     * 찜 행 id 를 따로 들고 있을 이유가 없다. 사용자 id 가 조건에 함께 들어가므로 남의 찜은
     * 지워지지 않는다.
     */
    @DeleteMapping("/{productModelId}")
    public ApiResponse<Void> remove(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long productModelId) {
        consumerWishlistService.remove(userId(jwt), productModelId);
        return ApiResponse.success(null);
    }

    /**
     * 내가 찜한 상품 id 전부.
     *
     * <p>SHOP 목록·상세·검색이 하트를 칠할 때 쓴다. 상품이 화면에 뜰 때마다 "이거 찜했나" 를
     * 물으면 한 페이지에 요청이 12번 붙는다 — 한 번 받아 두는 편이 낫다.
     */
    @GetMapping("/product-ids")
    public ApiResponse<List<Long>> findMyProductModelIds(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(consumerWishlistService.findMyProductModelIds(userId(jwt)));
    }

    /**
     * JWT {@code sub} 는 {@code JwtTokenService} 가 {@code String.valueOf(user.getId())} 로
     * 넣는다. 형식이 깨진 토큰은 여기 오기 전에 {@code JwtDecoder} 가 거부한다.
     */
    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
