package com.ssafy.upcycle.user.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.user.dto.BrandLogoUploadResponse;
import com.ssafy.upcycle.user.dto.CompanyProfileResponse;
import com.ssafy.upcycle.user.dto.CompanyProfileUpdateRequest;
import com.ssafy.upcycle.user.service.BrandLogoService;
import com.ssafy.upcycle.user.service.CompanyProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 사업자 계정 본인의 업체 정보(company_profile) 조회·수정.
 *
 * <p>가입 시점의 등록({@code SignupController}/{@code SignupService.registerBusiness})만 있고
 * 그 뒤 조회·수정 API 가 없던 것이 이 컨트롤러가 메우는 공백이다. {@code /processors/me},
 * {@code /api/v1/wallets/me} 와 같은 "내 리소스" 경로 관례({@code /me})를 그대로 따른다.
 *
 * <p>사용자는 JWT 의 {@code sub}(user id)에서 얻는다 — 경로 변수로 회사를 지정하게 하면
 * 다른 사업자의 정보를 들여다볼 길이 생긴다. 그래서 이 컨트롤러에는 {companyId} 같은
 * 경로 변수가 없다.
 */
@RestController
@RequestMapping("/api/v1/companies/me")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyProfileService companyProfileService;
    private final BrandLogoService brandLogoService;

    @GetMapping
    public ApiResponse<CompanyProfileResponse> myProfile(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(companyProfileService.myProfile(userId(jwt)));
    }

    @PutMapping
    public ApiResponse<CompanyProfileResponse> updateMyProfile(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CompanyProfileUpdateRequest request) {
        return ApiResponse.success(companyProfileService.updateMyProfile(userId(jwt), request));
    }

    /**
     * 브랜드 로고 업로드. <b>{@code BRAND} 만 가능하다.</b>
     *
     * <p>수정 폼의 {@code logoUrl} 자유 입력과 달리 파일을 받아 저장소에 넣고 저장 키를
     * 기록한다 — 소비자 화면에 그대로 걸리는 그림이라 우리가 받은 파일이어야 한다
     * ({@link BrandLogoService} 주석). 그 폼 필드 자체는 기존 화면이 깨지지 않도록 그대로 둔다.
     *
     * <p>응답의 {@code logoUrl} 은 <b>바로 쓸 수 있는 주소</b>다. 공개 경로라 브랜드 본인
     * 화면도 토큰 없이 {@code <img src>} 에 넣을 수 있다.
     */
    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BrandLogoUploadResponse> uploadLogo(
            @AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(brandLogoService.upload(userId(jwt), file));
    }

    /**
     * JWT {@code sub} 는 {@code JwtTokenService} 가 {@code String.valueOf(user.getId())} 로 넣는다.
     * 형식이 깨진 토큰은 여기 오기 전에 {@code JwtDecoder} 가 거부하므로 파싱 실패는 예상하지 않는다.
     */
    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
