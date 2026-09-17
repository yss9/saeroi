package com.ssafy.upcycle.user.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.user.dto.ConsumerProfileUpdateRequest;
import com.ssafy.upcycle.user.dto.UserSummaryResponse;
import com.ssafy.upcycle.user.service.ConsumerProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소비자 본인의 개인정보. <b>인증이 필요하고 {@code CONSUMER} 만 쓸 수 있다.</b>
 *
 * <p>경로에 사용자 id 가 없다 — 사용자는 JWT {@code sub} 에서 얻는다. 배송지·찜·장바구니가
 * 쓰는 "내 리소스" 관례를 그대로 따르며, <b>남의 개인정보를 가리키는 요청 자체가 만들어지지
 * 않는다.</b>
 *
 * <p><b>여기서 고치는 연락처는 계정 연락처다.</b> 배송지의 수령인 연락처와는 다른 값이고
 * 서로 동기화되지 않는다 — 가입 때만 같은 값으로 시작한다. 배송지는
 * {@code /api/v1/consumers/me/addresses} 가 맡는다.
 *
 * <p><b>비밀번호는 여기 없다.</b> 역할과 무관한 계정 기능이라 사업자도 쓸 수 있는
 * {@code PUT /api/v1/users/me/password} 가 받는다.
 */
@RestController
@RequestMapping("/api/v1/consumers/me")
@RequiredArgsConstructor
public class ConsumerProfileController {

    private final ConsumerProfileService consumerProfileService;

    /**
     * 내 개인정보.
     *
     * <p>로그인 응답으로 받은 값을 화면이 들고 있다가 쓰면 다른 탭에서 고쳤을 때 어긋난다.
     * 수정 폼이 현재 값을 채울 곳도 필요하다.
     */
    @GetMapping
    public ApiResponse<UserSummaryResponse> myProfile(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(consumerProfileService.myProfile(userId(jwt)));
    }

    /**
     * 이름·연락처를 바꾼다.
     *
     * <p>이메일은 받지 않는다 — 로그인 식별자라 새 주소의 소유 확인이 필요하고, 이름·연락처와
     * 같은 요청에 섞을 수 없다.
     */
    @PutMapping
    public ApiResponse<UserSummaryResponse> updateMyProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ConsumerProfileUpdateRequest request) {
        return ApiResponse.success(
                consumerProfileService.updateMyProfile(userId(jwt), request));
    }

    /**
     * JWT {@code sub} 는 {@code JwtTokenService} 가 {@code String.valueOf(user.getId())} 로
     * 넣는다. 형식이 깨진 토큰은 여기 오기 전에 {@code JwtDecoder} 가 거부한다.
     */
    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
