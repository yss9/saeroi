package com.ssafy.upcycle.product.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.product.ProductErrorCode;
import com.ssafy.upcycle.product.dto.ProductModelRegisterRequest;
import com.ssafy.upcycle.product.dto.ProductModelResponse;
import com.ssafy.upcycle.product.dto.ProductModelStatusRequest;
import com.ssafy.upcycle.product.dto.ProductModelUpdateRequest;
import com.ssafy.upcycle.product.entity.ProductItemStatus;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
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
    private final ProductSellerPolicy sellerPolicy;
    private final ProductImageService productImageService;

    /**
     * 제품 모델을 등록한다. <b>{@code DRAFT} 로 시작한다.</b>
     *
     * <p>{@code thumbnailUrl} 은 이미지 업로드가 돌려준 저장 키다. 저장소에 실제로 있는지
     * 확인하고 저장한다 — 확인하지 않으면 아무 문자열이나 통과하고, 이미지가 깨진 제품이
     * 마켓에 올라간 뒤에야 드러난다.
     *
     * @param brandId JWT {@code sub} 에서 온 사용자 id. 요청 바디로 받지 않는다
     */
    @Transactional
    public ProductModelResponse register(Long brandId, ProductModelRegisterRequest request) {
        sellerPolicy.requireSeller(brandId);
        productImageService.requireStoredImage(request.thumbnailUrl());

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
     * 제품 정보를 고친다. <b>보낸 필드만 바뀐다.</b>
     *
     * <p>수정 경로가 없으면 오타 하나 고치려고 제품을 새로 등록하게 되고, 그러면 단종된
     * 제품이 쌓이는 데다 <b>이미 생산된 개체가 옛 제품에 붙어</b> 이력이 갈라진다.
     *
     * <p><b>단종된 제품은 고칠 수 없다.</b> 더 팔지 않기로 한 제품의 이름과 가격을 바꾸면,
     * 그 제품을 산 사람이 나중에 보는 이력이 실제로 산 것과 달라진다.
     *
     * <p>대표 이미지를 바꾸면 등록과 같은 검사를 거친다 — 저장소에 없는 키를 넣으면
     * 이미지가 깨진 제품이 되고, 그건 마켓에 올라간 뒤에야 드러난다.
     */
    @Transactional
    public ProductModelResponse update(
            Long brandId, Long productModelId, ProductModelUpdateRequest request) {
        if (request.isEmpty() || request.hasBlankText()) {
            throw new BusinessException(ProductErrorCode.PRODUCT_MODEL_NOTHING_TO_UPDATE);
        }
        ProductModel model = productModelRepository.findById(productModelId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_MODEL_NOT_FOUND));
        requireOwner(model, brandId);
        requireNotDiscontinued(model);
        if (request.thumbnailUrl() != null) {
            productImageService.requireStoredImage(request.thumbnailUrl());
        }

        model.update(
                request.name(), request.price(), request.description(), request.thumbnailUrl());
        return ProductModelResponse.of(model, countInStock(productModelId));
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
}
