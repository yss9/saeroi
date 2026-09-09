package com.ssafy.upcycle.user.dto;

import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserStatus;

/**
 * 사업자 계정 본인의 업체 정보. {@code GET/PUT /api/v1/companies/me} 의 데이터다.
 *
 * <p>{@code representativeName}/{@code companyPhone} 은 {@code company_profile} 이 아니라
 * {@code users.name}/{@code users.phone} 에서 온다 — 사업자 가입 시
 * {@code SignupService.registerBusiness} 가 그렇게 채운다. {@code companyName}/{@code businessNumber}
 * 만 {@code company_profile} 고유 컬럼이다.
 */
public record CompanyProfileResponse(
        Long userId,
        String companyName,
        String businessNumber,
        String representativeName,
        String companyPhone,
        String zipCode,
        String address,
        String addressDetail,
        String introduction,
        String logoUrl,
        UserRole role,
        UserStatus status) {
}
