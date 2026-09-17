package com.ssafy.upcycle.user.entity;

import com.ssafy.upcycle.user.geolocation.GeoCoordinates;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

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

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "introduction", columnDefinition = "TEXT")
    private String introduction;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_approval_status", nullable = false)
    @ColumnDefault("'PENDING'")
    private CompanyRoleApprovalStatus roleApprovalStatus;

    @Column(name = "role_approved_wallet", length = 42)
    private String roleApprovedWallet;

    @Column(name = "role_approval_tx_hashes", columnDefinition = "TEXT")
    private String roleApprovalTxHashes;

    @Column(name = "role_reviewed_at")
    private LocalDateTime roleReviewedAt;

    @Column(name = "role_reviewed_by")
    private Long roleReviewedBy;

    private CompanyProfile(
            Long userId,
            String companyName,
            String businessNumber,
            String zipCode,
            String address,
            String addressDetail,
            GeoCoordinates coordinates) {
        this.userId = requireNonNull(userId, "userId");
        this.companyName = requireText(companyName, "companyName", 200);
        this.businessNumber = requireText(businessNumber, "businessNumber", 50);
        this.zipCode = requireText(zipCode, "zipCode", 20);
        this.address = requireText(address, "address", 255);
        this.addressDetail = optionalText(addressDetail, "addressDetail", 255);
        this.roleApprovalStatus = CompanyRoleApprovalStatus.PENDING;
        updateCoordinates(coordinates);
    }

    public static CompanyProfile register(
            Long userId,
            String companyName,
            String businessNumber,
            String zipCode,
            String address,
            String addressDetail,
            GeoCoordinates coordinates) {
        return new CompanyProfile(
                userId, companyName, businessNumber, zipCode, address, addressDetail, coordinates);
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
            String logoUrl,
            GeoCoordinates coordinates) {
        this.zipCode = requireText(zipCode, "zipCode", 20);
        this.address = requireText(address, "address", 255);
        this.addressDetail = optionalText(addressDetail, "addressDetail", 255);
        this.introduction = optionalText(introduction, "introduction", 65_535);
        this.logoUrl = optionalText(logoUrl, "logoUrl", 500);
        updateCoordinates(coordinates);
    }

    /** 기존 업체 행의 일회성 backfill에서 주소는 유지하고 좌표만 채우는 경로. */
    public void updateCoordinates(GeoCoordinates coordinates) {
        GeoCoordinates required = requireNonNull(coordinates, "coordinates");
        this.latitude = required.latitude();
        this.longitude = required.longitude();
    }

    /** 소개글만 바꾸는 경로. 데모 데이터 시드가 주소를 다시 보내지 않고 쓴다. */
    public void updateIntroduction(String introduction) {
        this.introduction = optionalText(introduction, "introduction", 65_535);
    }

    /** 대표 이미지 업로드가 주소나 소개를 다시 보내지 않고 저장 키만 바꾸는 경로. */
    public void updateLogoUrl(String logoUrl) {
        this.logoUrl = optionalText(logoUrl, "logoUrl", 500);
    }

    public void approveRole(String walletAddress, String transactionHashes, Long adminId, LocalDateTime now) {
        this.roleApprovalStatus = CompanyRoleApprovalStatus.APPROVED;
        this.roleApprovedWallet = requireText(walletAddress, "roleApprovedWallet", 42);
        this.roleApprovalTxHashes = transactionHashes;
        this.roleReviewedBy = requireNonNull(adminId, "roleReviewedBy");
        this.roleReviewedAt = requireNonNull(now, "roleReviewedAt");
    }

    public void rejectRole(Long adminId, LocalDateTime now) {
        this.roleApprovalStatus = CompanyRoleApprovalStatus.REJECTED;
        this.roleApprovedWallet = null;
        this.roleApprovalTxHashes = null;
        this.roleReviewedBy = requireNonNull(adminId, "roleReviewedBy");
        this.roleReviewedAt = requireNonNull(now, "roleReviewedAt");
    }

    /** 지갑이 바뀌면 이전 주소의 승인 결과를 새 주소에 승계하지 않는다. */
    public void resetRoleApprovalForWallet(String walletAddress) {
        if (roleApprovedWallet != null && roleApprovedWallet.equalsIgnoreCase(walletAddress)) return;
        this.roleApprovalStatus = CompanyRoleApprovalStatus.PENDING;
        this.roleApprovedWallet = null;
        this.roleApprovalTxHashes = null;
        this.roleReviewedBy = null;
        this.roleReviewedAt = null;
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
