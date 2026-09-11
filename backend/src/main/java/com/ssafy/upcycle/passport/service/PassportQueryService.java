package com.ssafy.upcycle.passport.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.passport.PassportErrorCode;
import com.ssafy.upcycle.passport.dto.PassportResponse;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.traceability.dto.ProductJourneyResponse;
import com.ssafy.upcycle.traceability.service.ProductJourneyQueryService;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * QR 로 찍은 제품의 공개 조회. 인증이 없는 경로라 {@code qrToken} 자체가 접근 통제다
 * (§6-3, 2026-09-08).
 */
@Service
@RequiredArgsConstructor
public class PassportQueryService {

    private final ProductItemRepository productItemRepository;
    private final ProductModelRepository productModelRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final ProductJourneyQueryService productJourneyQueryService;

    @Transactional(readOnly = true)
    public PassportResponse findByQrToken(String qrToken) {
        ProductItem item = productItemRepository.findByQrToken(qrToken)
                .orElseThrow(() -> new BusinessException(PassportErrorCode.QR_TOKEN_NOT_FOUND));
        // 개체가 있으면 모델은 반드시 있다 — product_model_id 는 등록 시점에 검증된 FK 라
        // 여기서 다시 없는 경우를 매핑할 필요가 없다. 없다면 데이터 정합성이 깨진 것이고
        // 그 자체가 이 요청과 무관한 버그다.
        ProductModel model = productModelRepository.findById(item.getProductModelId())
                .orElseThrow(() -> new BusinessException(PassportErrorCode.QR_TOKEN_NOT_FOUND));
        String brandName = companyProfileRepository.findById(model.getBrandId())
                .map(profile -> profile.getCompanyName())
                .orElse(null);
        ProductJourneyResponse journey = productJourneyQueryService.findJourney(item).orElse(null);
        return PassportResponse.of(item, model, brandName, journey);
    }

    /**
     * 썸네일 조회가 필요로 하는 {@code productModelId} 만 뽑아 낸다.
     *
     * <p>{@code ProductImageService.readThumbnail} 이 이미 productModelId 를 받게
     * 돼 있어(제품 상세와 같은 이유 — 키가 아니라 행에서 꺼낸다) 그 계약을 그대로
     * 재사용한다. 이 메서드는 {@code qrToken → productModelId} 변환만 맡는다.
     */
    @Transactional(readOnly = true)
    public Long resolveProductModelId(String qrToken) {
        return productItemRepository.findByQrToken(qrToken)
                .map(ProductItem::getProductModelId)
                .orElseThrow(() -> new BusinessException(PassportErrorCode.QR_TOKEN_NOT_FOUND));
    }
}
