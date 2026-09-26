package com.ssafy.upcycle.user.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.user.dto.ConsumerAddressRequest;
import com.ssafy.upcycle.user.dto.ConsumerAddressResponse;
import com.ssafy.upcycle.user.service.ConsumerAddressService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

/**
 * CONSUMER 계정 본인의 배송지 주소록.
 *
 * <p>사업자의 {@code /api/v1/companies/me} 와 짝을 이룬다. 다른 점은 배송지가 1:N 이라
 * {@code /me} 아래에 컬렉션이 붙는다는 것뿐이다.
 *
 * <p>사용자는 JWT 의 {@code sub}(user id)에서 얻는다 — 경로 변수로 사용자를 지정하게 하면
 * 남의 주소록을 들여다볼 길이 생긴다. 그래서 {@code {userId}} 같은 경로 변수가 없다.
 * {@code {addressId}} 는 있지만, 서비스가 소유자를 확인하고 남의 것이면 404 로 답한다.
 */
@RestController
@RequestMapping("/api/v1/consumers/me/addresses")
@RequiredArgsConstructor
@Validated
public class ConsumerAddressController {

    private final ConsumerAddressService consumerAddressService;

    @GetMapping
    public ApiResponse<List<ConsumerAddressResponse>> myAddresses(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(consumerAddressService.myAddresses(userId(jwt)));
    }

    @PostMapping
    public ApiResponse<ConsumerAddressResponse> add(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ConsumerAddressRequest request) {
        return ApiResponse.success(consumerAddressService.add(userId(jwt), request));
    }

    @PutMapping("/{addressId}")
    public ApiResponse<ConsumerAddressResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive(message = "배송지 id는 양수여야 합니다.") Long addressId,
            @Valid @RequestBody ConsumerAddressRequest request) {
        return ApiResponse.success(consumerAddressService.update(userId(jwt), addressId, request));
    }

    @DeleteMapping("/{addressId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive(message = "배송지 id는 양수여야 합니다.") Long addressId) {
        consumerAddressService.delete(userId(jwt), addressId);
        return ApiResponse.success(null);
    }

    /**
     * 기본 배송지 지정.
     *
     * <p>수정({@code PUT})과 분리한 이유는 {@code ConsumerAddressRequest} 문서에 적었다 —
     * 주소를 고치다가 기본 배송지가 조용히 옮겨 가면 사용자가 눈치채지 못한 채 다른 곳으로
     * 물건을 받는다.
     */
    @PatchMapping("/{addressId}/default")
    public ApiResponse<ConsumerAddressResponse> setDefault(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive(message = "배송지 id는 양수여야 합니다.") Long addressId) {
        return ApiResponse.success(consumerAddressService.setDefault(userId(jwt), addressId));
    }

    /**
     * JWT {@code sub} 는 {@code JwtTokenService} 가 {@code String.valueOf(user.getId())} 로 넣는다.
     * 형식이 깨진 토큰은 여기 오기 전에 {@code JwtDecoder} 가 거부하므로 파싱 실패는 예상하지 않는다.
     */
    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
