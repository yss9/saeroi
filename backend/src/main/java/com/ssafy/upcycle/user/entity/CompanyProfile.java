package com.ssafy.upcycle.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** SUPPLIER, BRAND, PROCESSOR 계정의 업체 정보. */
@Getter
@Entity
@Table(name = "company_profile")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanyProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "business_number", nullable = false, length = 50)
    private String businessNumber;

    @Column(name = "zip_code", nullable = false, length = 20)
    private String zipCode;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "address_detail", length = 255)
    private String addressDetail;

    @Column(name = "introduction", columnDefinition = "TEXT")
    private String introduction;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    private CompanyProfile(
            Long userId,
            String companyName,
            String businessNumber,
            String zipCode,
            String address,
            String addressDetail) {
        this.userId = requireNonNull(userId, "userId");
        this.companyName = requireText(companyName, "companyName", 200);
        this.businessNumber = requireText(businessNumber, "businessNumber", 50);
        this.zipCode = requireText(zipCode, "zipCode", 20);
        this.address = requireText(address, "address", 255);
        this.addressDetail = optionalText(addressDetail, "addressDetail", 255);
    }

    public static CompanyProfile register(
            Long userId,
            String companyName,
            String businessNumber,
            String zipCode,
            String address,
            String addressDetail) {
        return new CompanyProfile(
                userId, companyName, businessNumber, zipCode, address, addressDetail);
    }

    /**
     * 주소·소개·로고를 갱신한다.
     *
     * <p>{@code companyName}/{@code businessNumber} 는 여기 없다 — 사업자 등록 시 확정되는
     * 식별 정보라 계정 소유자가 임의로 바꿀 대상이 아니라고 판단했다
     * ({@code CompanyProfileService} 문서 참고).
     */
    public void updateProfile(
            String zipCode,
            String address,
            String addressDetail,
            String introduction,
            String logoUrl) {
        this.zipCode = requireText(zipCode, "zipCode", 20);
        this.address = requireText(address, "address", 255);
        this.addressDetail = optionalText(addressDetail, "addressDetail", 255);
        this.introduction = optionalText(introduction, "introduction", 65_535);
        this.logoUrl = optionalText(logoUrl, "logoUrl", 500);
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("CompanyProfile " + field + " must not be null");
        }
        return value;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CompanyProfile " + field + " must not be blank");
        }
        return checkLength(value, field, maxLength);
    }

    private static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return checkLength(value, field, maxLength);
    }

    private static String checkLength(String value, String field, int maxLength) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    "CompanyProfile " + field + " must not exceed " + maxLength + " characters");
        }
        return value;
    }
}
