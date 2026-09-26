package com.ssafy.upcycle.user.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.trade.dto.PageResponse;
import com.ssafy.upcycle.user.CompanyErrorCode;
import com.ssafy.upcycle.user.dto.PublicBrandResponse;
import com.ssafy.upcycle.user.dto.PublicBrandSearchCondition;
import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import com.ssafy.upcycle.user.repository.PublicBrandQueryRepository;
import com.ssafy.upcycle.user.repository.PublicBrandQueryRepository.PublicBrandRow;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비회원에게 공개하는 브랜드 카탈로그.
 *
 * <p>공개 기준이 하나다 — <b>판매 중이면서 소비자에게 노출되는 상품이 하나 이상</b>. 상품이
 * 없는 브랜드가 목록에 뜨면 눌렀을 때 빈 화면이 되고, {@code ETC} 상품만 가진 브랜드도 같은
 * 이유로 나오지 않아야 한다. 그 판정은 {@link PublicBrandQueryRepository} 가 상품 목록과 같은
 * 조건으로 한다.
 *
 * <p><b>목록·상세가 같은 기준을 쓴다.</b> 목록에서만 빼면 id 를 아는 사람에게 빈 상세가 열린다.
 *
 * <p>소개글과 로고는 <b>페이지에 실린 브랜드만</b> 따로 읽는다. 브랜드마다 부르면 그게 N+1
 * 이고, 소개글은 TEXT 라 그룹 질의에 끌고 들어갈 값도 아니다 — 상품 목록이 재고와 갤러리를
 * 한 번에 모아 오는 것과 같은 방식이다.
 */
@Service
@RequiredArgsConstructor
public class PublicBrandCatalogService {

    /** 회사명도 사용자 이름도 없는 경우. 상품 목록이 쓰는 문구와 같다. */
    private static final String UNKNOWN_BRAND = "알 수 없는 브랜드";

    private final PublicBrandQueryRepository publicBrandQueryRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<PublicBrandResponse> search(PublicBrandSearchCondition condition) {
        Pageable pageable = PageRequest.of(condition.pageOrDefault(), condition.sizeOrDefault());
        Page<PublicBrandRow> brands = publicBrandQueryRepository.search(condition, pageable);

        Map<Long, CompanyProfile> profiles = profilesOf(
                brands.getContent().stream().map(PublicBrandRow::brandId).toList());
        return PageResponse.from(brands.map(row -> toResponse(row, profiles.get(row.brandId()))));
    }

    /**
     * 브랜드 상세.
     *
     * <p>목록에 실릴 자격이 없으면 404 다 — 판매 중인 공개 상품이 하나도 없는 브랜드의 상세는
     * 브랜드명만 있고 아무것도 없는 화면이 된다.
     */
    @Transactional(readOnly = true)
    public PublicBrandResponse findDetail(Long brandId) {
        long productCount = publicBrandQueryRepository.countConsumerVisibleProducts(brandId);
        if (productCount == 0) {
            throw new BusinessException(CompanyErrorCode.PUBLIC_BRAND_NOT_FOUND);
        }
        CompanyProfile profile = companyProfileRepository.findById(brandId).orElse(null);
        return toResponse(
                new PublicBrandRow(brandId, brandName(brandId, profile), productCount), profile);
    }

    private PublicBrandResponse toResponse(PublicBrandRow row, CompanyProfile profile) {
        return new PublicBrandResponse(
                row.brandId(),
                row.brandName() == null ? UNKNOWN_BRAND : row.brandName(),
                blankToNull(profile == null ? null : profile.getIntroduction()),
                BrandLogoService.publicLogoUrlOrNull(
                        row.brandId(), profile == null ? null : profile.getLogoUrl()),
                row.productCount());
    }

    /**
     * 빈 문자열을 {@code null} 로 접는다.
     *
     * <p>저장 시점에 이미 접히지만({@code CompanyProfile.optionalText}) 응답에서 한 번 더
     * 본다 — 화면이 "없음" 을 두 가지 모양으로 다루게 되는 것이 이 한 줄보다 비싸다.
     */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** 상세에서만 쓴다. 목록은 질의가 이미 같은 규칙으로 이름을 만들어 온다. */
    private String brandName(Long brandId, CompanyProfile profile) {
        if (profile != null && profile.getCompanyName() != null) {
            return profile.getCompanyName();
        }
        return userRepository.findById(brandId).map(User::getName).orElse(UNKNOWN_BRAND);
    }

    private Map<Long, CompanyProfile> profilesOf(List<Long> brandIds) {
        if (brandIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, CompanyProfile> profiles = new LinkedHashMap<>();
        for (CompanyProfile profile : companyProfileRepository.findAllById(brandIds)) {
            profiles.put(profile.getUserId(), profile);
        }
        return profiles;
    }
}
