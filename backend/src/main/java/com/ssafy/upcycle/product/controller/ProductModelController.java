package com.ssafy.upcycle.product.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.product.dto.ProductModelRegisterRequest;
import com.ssafy.upcycle.product.dto.ProductModelResponse;
import com.ssafy.upcycle.product.dto.ProductModelStatusRequest;
import com.ssafy.upcycle.product.service.ProductModelRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 제품 모델 등록·판매 상태 관리. <b>인증이 필요하다.</b>
 *
 * <p>{@code SecurityConfig} 의 permitAll 목록은 {@code /api/v1/auth/**} 와
 * {@code GET /api/health} 뿐이고 나머지는 {@code anyRequest().authenticated()} 로 떨어지므로,
 * 여기는 아무것도 하지 않아도 토큰이 필요하다.
 *
 * <p><b>역할 검사를 {@code @PreAuthorize} 로 하지 않는다.</b> 그것은 토큰의 역할 클레임을
 * 보는데, 토큰은 발급 시점의 스냅샷이라 정지되거나 역할이 바뀐 계정이 만료 전까지 계속
 * 통과한다. 소재 등록과 같은 이유로 서비스가 DB 를 다시 읽어 판단한다.
 *
 * <p>경로는 {@code /api/v1/...} 다 — 거래·지갑 계열과 같은 관례다.
 * ({@code /api/materials} 만 무버전인데, 그쪽은 티켓이 무버전으로 적혀 있어서다.)
 */
@RestController
@RequestMapping("/api/v1/product-models")
@RequiredArgsConstructor
public class ProductModelController {

    private final ProductModelRegistrationService productModelRegistrationService;

    /**
     * 제품 등록. {@code BRAND} 만 가능하다.
     *
     * <p>브랜드는 JWT {@code sub} 에서 얻는다 — 바디로 받으면 남의 이름으로 제품을 올릴 수
     * 있다.
     *
     * <p>응답의 {@code status} 는 언제나 {@code DRAFT}, {@code stockQuantity} 는 {@code 0}
     * 이다. 등록만으로는 팔 개체가 없기 때문이며, 판매 시작은 아래 상태 변경 경로다.
     */
    @PostMapping
    public ApiResponse<ProductModelResponse> register(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProductModelRegisterRequest request) {
        return ApiResponse.success(productModelRegistrationService.register(userId(jwt), request));
    }

    /**
     * 판매 시작 / 판매 중단.
     *
     * <p>{@code PATCH} 인 것은 모델의 일부(상태)만 바꾸기 때문이다. 상태 하나만 받는 이유는
     * {@link ProductModelStatusRequest} 주석 참고 — {@code SOLD_OUT} 은 손으로 지정할 수 없다.
     *
     * <p>본인 제품만 바꿀 수 있다. 역할만 보면 브랜드 아무나 남의 제품을 단종시킬 수 있다.
     */
    @PatchMapping("/{productModelId}/status")
    public ApiResponse<ProductModelResponse> changeStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long productModelId,
            @Valid @RequestBody ProductModelStatusRequest request) {
        return ApiResponse.success(
                productModelRegistrationService.changeStatus(userId(jwt), productModelId, request));
    }

    /**
     * JWT {@code sub} 는 {@code JwtTokenService} 가 {@code String.valueOf(user.getId())} 로 넣는다.
     * 형식이 깨진 토큰은 여기 오기 전에 {@code JwtDecoder} 가 거부한다.
     */
    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
