package com.ssafy.upcycle.product.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.product.ProductErrorCode;
import com.ssafy.upcycle.product.dto.ProductModelRegisterRequest;
import com.ssafy.upcycle.product.dto.ProductModelResponse;
import com.ssafy.upcycle.product.dto.ProductModelStatusRequest;
import com.ssafy.upcycle.product.entity.ProductItemStatus;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제품 모델 등록과 판매 상태 변경.
 *
 * <p><b>등록과 판매 시작을 나눈 이유.</b> 등록 시점에는 팔 개체가 하나도 없다. 개체는 생산
 * 배치가 확정될 때 생기므로, 등록 즉시 판매 중으로 만들면 마켓에 뜨자마자 살 수 없는 제품이
 * 된다. 그래서 {@code DRAFT} 로 시작하고 재고가 생긴 뒤에 판매를 연다.
 */
@Service
@RequiredArgsConstructor
public class ProductModelRegistrationService {

    private final ProductModelRepository productModelRepository;
    private final ProductItemRepository productItemRepository;
    private final UserRepository userRepository;

    /**
     * 제품 모델을 등록한다. <b>{@code DRAFT} 로 시작한다.</b>
     *
     * @param brandId JWT {@code sub} 에서 온 사용자 id. 요청 바디로 받지 않는다
     */
    @Transactional
    public ProductModelResponse register(Long brandId, ProductModelRegisterRequest request) {
        requireProductCapableUser(brandId);

        ProductModel saved = productModelRepository.save(ProductModel.register(
                request.name(),
                request.category(),
                brandId,
                request.price(),
                request.description(),
                request.thumbnailUrl()));

        // 방금 만든 모델이라 재고는 언제나 0 이다. 세지 않고 상수를 넣으면 나중에 등록
        // 경로가 개체를 함께 만들게 됐을 때 조용히 거짓말을 한다.
        return ProductModelResponse.of(saved, countInStock(saved.getId()));
    }

    /**
     * 판매 상태를 바꾼다.
     *
     * <p>{@code SOLD_OUT} 은 여기서 다루지 않는다. 품절은 브랜드가 정하는 값이 아니라 개체를
     * 세어 나오는 사실이고, 판매·취소 처리가 전환한다.
     */
    @Transactional
    public ProductModelResponse changeStatus(
            Long brandId, Long productModelId, ProductModelStatusRequest request) {
        ProductModel model = productModelRepository.findById(productModelId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_MODEL_NOT_FOUND));
        requireOwner(model, brandId);
        requireNotDiscontinued(model);

        long stock = countInStock(productModelId);
        switch (request.action()) {
            case ON_SALE -> {
                // 개체가 없는 제품을 마켓에 올리면 들어오자마자 살 수 없다.
                if (stock == 0) {
                    throw new BusinessException(ProductErrorCode.PRODUCT_MODEL_HAS_NO_STOCK);
                }
                model.putOnSale();
            }
            case DISCONTINUE -> model.discontinue();
        }
        return ProductModelResponse.of(model, stock);
    }

    /** 재고는 컬럼이 아니라 개체를 세어 구한다. 이 도메인의 규칙이다. */
    private long countInStock(Long productModelId) {
        return productItemRepository.countByProductModelIdAndStatus(
                productModelId, ProductItemStatus.IN_STOCK);
    }

    /**
     * 남의 제품을 건드릴 수 없다.
     *
     * <p>역할 검사만으로는 부족하다 — BRAND 라면 누구나 다른 브랜드의 제품을 단종시킬 수
     * 있게 된다.
     */
    private void requireOwner(ProductModel model, Long brandId) {
        if (!model.getBrandId().equals(brandId)) {
            throw new BusinessException(ProductErrorCode.NOT_PRODUCT_MODEL_OWNER);
        }
    }

    /**
     * 상태를 엔티티에 맡기지 않고 여기서 먼저 본다. 엔티티는 {@code IllegalStateException} 을
     * 던지는데 그대로 두면 500 이 나간다. 엔티티 쪽 검사는 최후 방어선으로 남긴다.
     */
    private void requireNotDiscontinued(ProductModel model) {
        if (model.getStatus() == ProductModelStatus.DISCONTINUED) {
            throw new BusinessException(ProductErrorCode.PRODUCT_MODEL_DISCONTINUED);
        }
    }

    /**
     * 등록 자격을 확인한다.
     *
     * <p>JWT 의 {@code roles} 클레임을 믿지 않고 DB 를 다시 읽는다. 토큰은 발급 시점의
     * 스냅샷이라, 정지되거나 역할이 바뀐 계정이 만료 전까지 등록을 계속할 수 있다.
     * 소재 등록이 같은 이유로 같은 방식을 쓴다.
     */
    private void requireProductCapableUser(Long brandId) {
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
