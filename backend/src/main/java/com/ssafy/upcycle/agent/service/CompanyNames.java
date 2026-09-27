package com.ssafy.upcycle.agent.service;

import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 담당자에게 보일 <b>업체 이름</b>.
 *
 * <p>판매 글과 가공업체 검색이 돌려주는 이름은 가입한 대표자 개인 이름이다. 그래서 결제 카드에 "판매자
 * 공병우" 처럼 사람 이름이 떴고, 가공업체는 여러 곳이 "홍길동" 으로 보였다. 담당자가 거래하는 상대는
 * 업체라 업체명(`company_profile.company_name`)을 쓴다. 업체 정보가 없으면 원래 이름을 그대로 쓴다.
 *
 * <p>공용 응답(`ListingResponse` 등)은 건드리지 않는다 — 에이전트가 보여 주는 이름만 바꾼다.
 */
@Component
public class CompanyNames {

    private final CompanyProfileRepository profiles;

    public CompanyNames(CompanyProfileRepository profiles) {
        this.profiles = profiles;
    }

    /** 업체명. 업체 정보가 없거나 읽지 못하면 {@code fallback}(대표자 이름). */
    public String of(Long userId, String fallback) {
        if (userId == null) {
            return fallback;
        }
        try {
            return profiles.findById(userId)
                    .map(CompanyProfile::getCompanyName)
                    .filter(name -> !name.isBlank())
                    .orElse(fallback);
        } catch (RuntimeException unreadable) {
            return fallback;
        }
    }

    /** 여러 명을 한 번에. 업체 정보가 있는 사람만 담긴다 — 없으면 부르는 쪽이 원래 이름을 쓴다. */
    public Map<Long, String> of(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        try {
            return profiles.findAllById(userIds.stream().filter(Objects::nonNull).distinct().toList()).stream()
                    .filter(profile -> profile.getCompanyName() != null && !profile.getCompanyName().isBlank())
                    .collect(Collectors.toMap(CompanyProfile::getUserId, CompanyProfile::getCompanyName,
                            (first, second) -> first));
        } catch (RuntimeException unreadable) {
            return Map.of();
        }
    }

    /** {@link #of(Collection)} 결과에서 한 사람의 이름을 꺼낸다. */
    public static String pick(Map<Long, String> names, Long userId, String fallback) {
        return userId == null ? fallback : names.getOrDefault(userId, fallback);
    }
}
