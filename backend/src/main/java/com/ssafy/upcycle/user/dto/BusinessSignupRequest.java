package com.ssafy.upcycle.user.dto;

import com.ssafy.upcycle.user.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 사업자회원 가입 요청. */
public record BusinessSignupRequest(
        @NotNull(message = "업체 유형은 필수입니다.")
        UserRole role,

        @NotBlank(message = "업체명은 필수입니다.")
        @Size(min = 2, max = 200, message = "업체명은 2자 이상 200자 이하여야 합니다.")
        String companyName,

        @NotBlank(message = "사업자등록번호는 필수입니다.")
        @Pattern(
                regexp = "^\\d{3}-?\\d{2}-?\\d{5}$",
                message = "사업자등록번호 형식이 올바르지 않습니다.")
        String businessNumber,

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

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "이메일은 255자를 초과할 수 없습니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s]).{8,255}$",
                message = "비밀번호는 영문, 숫자, 특수문자를 포함해 8자 이상이어야 합니다.")
        String password) {
}
