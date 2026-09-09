package com.ssafy.upcycle.passport.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.passport.PassportErrorCode;
import com.ssafy.upcycle.passport.dto.PassportResponse;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
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

    @Transactional(readOnly = true)
    public PassportResponse findByQrToken(String qrToken) {
        ProductItem item = productItemRepository.findByQrToken(qrToken)
                .orElseThrow(() -> new BusinessException(PassportErrorCode.QR_TOKEN_NOT_FOUND));
        // 개체가 있으면 모델은 반드시 있다 — product_model_id 는 등록 시점에 검증된 FK 라
        // 여기서 다시 없는 경우를 매핑할 필요가 없다. 없다면 데이터 정합성이 깨진 것이고
        // 그 자체가 이 요청과 무관한 버그다.
        ProductModel model = productModelRepository.findById(item.getProductModelId())
                .orElseThrow(() -> new BusinessException(PassportErrorCode.QR_TOKEN_NOT_FOUND));
        return PassportResponse.of(item, model);
    }
}
