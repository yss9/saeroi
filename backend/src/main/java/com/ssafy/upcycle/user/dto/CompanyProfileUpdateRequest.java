package com.ssafy.upcycle.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 업체 정보 수정 요청. {@code PUT /api/v1/companies/me}.
 *
 * <p>{@code companyName}/{@code businessNumber} 는 없다 — 사업자 등록 시 확정되는 식별 정보라
 * 수정 대상에서 제외했다. 근거: {@code CompanyProfileService} 클래스 문서.
 */
public record CompanyProfileUpdateRequest(
        @NotBlank(message = "대표자명은 필수입니다.")
        @Size(min = 2, max = 100, message = "대표자명은 2자 이상 100자 이하여야 합니다.")
        String representativeName,

        @NotBlank(message = "업체 연락처는 필수입니다.")
        @Pattern(
                regexp = "^0\\d{1,2}-?\\d{3,4}-?\\d{4}$",
                message = "업체 연락처 형식이 올바르지 않습니다.")
        String companyPhone,

        @NotBlank(message = "우편번호는 필수입니다.")
        @Size(max = 20, message = "우편번호는 20자를 초과할 수 없습니다.")
        String zipCode,

        @NotBlank(message = "업체 주소는 필수입니다.")
        @Size(max = 255, message = "업체 주소는 255자를 초과할 수 없습니다.")
        String address,

        @Size(max = 255, message = "상세 주소는 255자를 초과할 수 없습니다.")
        String addressDetail,

        @Size(max = 2000, message = "업체 소개는 2000자를 초과할 수 없습니다.")
        String introduction,

        @Size(max = 500, message = "로고 URL 은 500자를 초과할 수 없습니다.")
        String logoUrl) {
}
