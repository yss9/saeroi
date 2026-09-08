package com.ssafy.upcycle.product.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.product.ProductErrorCode;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 제품을 팔 자격이 있는가. <b>등록 · 이미지 업로드 · 판매 시작이 같은 답을 써야 한다.</b>
 *
 * <p>서비스마다 같은 검사를 복사해 두면 조건이 갈라진다. 실제로 그렇게 됐었다 — 등록은
 * 정지 계정을 막는데 판매 시작은 막지 않아, 같은 계정이 한쪽 문은 못 지나가고 다른 쪽
 * 문은 지나가는 상태가 있었다. 판정을 한곳에 두면 그 어긋남이 생길 자리가 없어진다.
 *
 * <p><b>JWT 의 {@code roles} 클레임을 믿지 않고 DB 를 다시 읽는다.</b> 토큰은 발급 시점의
 * 스냅샷이라, 정지되거나 역할이 바뀐 계정이 만료 전까지 계속 통과한다. 소재 등록이 같은
 * 이유로 같은 방식을 쓴다.
 */
@Component
@RequiredArgsConstructor
public class ProductSellerPolicy {

    private final UserRepository userRepository;

    /**
     * @param brandId JWT {@code sub} 에서 온 사용자 id
     * @throws BusinessException 계정이 없거나, 정지됐거나, 브랜드가 아닌 경우
     */
    public void requireSeller(Long brandId) {
        User user = userRepository.findById(brandId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_BRAND_NOT_FOUND));
        if (!user.isActive()) {
            throw new BusinessException(ProductErrorCode.PRODUCT_BRAND_SUSPENDED);
        }
        if (!user.getRole().canRegisterProduct()) {
            throw new BusinessException(ProductErrorCode.PRODUCT_ROLE_NOT_ALLOWED);
        }
    }
}
