package com.ssafy.upcycle.user.service;

import static com.ssafy.upcycle.user.CompanyErrorCode.COMPANY_PROFILE_FORBIDDEN;
import static com.ssafy.upcycle.user.CompanyErrorCode.COMPANY_PROFILE_NOT_FOUND;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.user.dto.CompanyProfileResponse;
import com.ssafy.upcycle.user.dto.CompanyProfileUpdateRequest;
import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.geolocation.AddressGeocoder;
import com.ssafy.upcycle.user.geolocation.GeoCoordinates;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사업자 계정 본인의 업체 정보 조회·수정. {@code CompanyController} 가 호출한다.
 *
 * <p><b>대상 역할</b>은 {@code UserRole.canOwnWallet()}(SUPPLIER/BRAND/PROCESSOR)과 같은 집합을
 * 쓴다 — {@code company_profile} 을 갖는 계정과 지갑을 가질 수 있는 계정이 ERD 상 정확히 같은
 * 3종이기 때문이다({@code ERD.md} §"users" 하위). CONSUMER/ADMIN 이 호출하면
 * {@link com.ssafy.upcycle.user.CompanyErrorCode#COMPANY_PROFILE_FORBIDDEN} 이다.
 *
 * <p><b>수정 가능한 필드</b>는 대표자명({@code users.name})·업체 연락처({@code users.phone})·
 * 주소 3분할·소개·로고뿐이다. {@code companyName}/{@code businessNumber} 는 사업자 등록 시
 * 확정되는 식별 정보이므로 뺐다 — 사업자등록번호는 세무 신고상의 고정 식별자이고, 업체명은
 * 온보딩 단계 검증(신뢰 표시)의 근거라 계정 소유자가 조용히 바꾸면 그 근거가 무너진다.
 * 실제 스키마에는 {@code business_number} UNIQUE 제약이 없어(참고: 08-divergences.md §8)
 * DB 가 이를 강제하지 않으므로, 애플리케이션이 막지 않으면 아무 안전장치가 없다.
 */
@Service
@RequiredArgsConstructor
public class CompanyProfileService {

    private final UserRepository users;
    private final CompanyProfileRepository profiles;
    private final AddressGeocoder addressGeocoder;

    @Transactional(readOnly = true)
    public CompanyProfileResponse myProfile(Long userId) {
        User user = requireBusinessUser(userId);
        CompanyProfile profile = requireProfile(userId);
        return toResponse(user, profile);
    }

    @Transactional
    public CompanyProfileResponse updateMyProfile(Long userId, CompanyProfileUpdateRequest request) {
        User user = requireBusinessUser(userId);
        CompanyProfile profile = requireProfile(userId);
        String address = request.address().trim();
        GeoCoordinates coordinates = currentOrGeocodedCoordinates(profile, address);

        user.updateContact(request.representativeName(), request.companyPhone());
        profile.updateProfile(
                request.zipCode(),
                address,
                request.addressDetail(),
                request.introduction(),
                request.logoUrl(),
                coordinates);

        return toResponse(user, profile);
    }

    private GeoCoordinates currentOrGeocodedCoordinates(
            CompanyProfile profile, String requestedAddress) {
        if (requestedAddress.equals(profile.getAddress())
                && profile.getLatitude() != null
                && profile.getLongitude() != null) {
            return new GeoCoordinates(profile.getLatitude(), profile.getLongitude());
        }
        return addressGeocoder.geocode(requestedAddress);
    }

    private User requireBusinessUser(Long userId) {
        return users.findById(userId)
                .filter(user -> user.isActive() && user.getRole().canOwnWallet())
                .orElseThrow(() -> new BusinessException(COMPANY_PROFILE_FORBIDDEN));
    }

    private CompanyProfile requireProfile(Long userId) {
        return profiles.findById(userId)
                .orElseThrow(() -> new BusinessException(COMPANY_PROFILE_NOT_FOUND));
    }

    private CompanyProfileResponse toResponse(User user, CompanyProfile profile) {
        return new CompanyProfileResponse(
                user.getId(),
                profile.getCompanyName(),
                profile.getBusinessNumber(),
                user.getName(),
                user.getPhone(),
                profile.getZipCode(),
                profile.getAddress(),
                profile.getAddressDetail(),
                profile.getLatitude(),
                profile.getLongitude(),
                profile.getIntroduction(),
                profile.getLogoUrl(),
                user.getRole(),
                user.getStatus(),
                profile.getRoleApprovalStatus());
    }
}
