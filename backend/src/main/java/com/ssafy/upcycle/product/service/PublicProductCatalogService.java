package com.ssafy.upcycle.product.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.common.storage.FileContent;
import com.ssafy.upcycle.product.ProductErrorCode;
import com.ssafy.upcycle.product.dto.PublicProductModelResponse;
import com.ssafy.upcycle.product.dto.PublicProductModelSearchCondition;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductItemStatus;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.product.repository.ProductModelSearchQueryRepository;
import com.ssafy.upcycle.product.repository.ProductStockQueryRepository;
import com.ssafy.upcycle.trade.dto.PageResponse;
import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
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
 * 비회원에게 노출 가능한 판매 중 상품만 조회한다.
 *
 * <p>공개 여부의 조건이 둘이다 — {@code ON_SALE} 이어야 하고, 소비자에게 노출하는
 * 분류여야 한다 ({@link ProductCategory#isConsumerVisible}). {@code ETC} 가 후자에 걸린다.
 *
 * <p><b>목록·상세·썸네일이 같은 기준을 쓴다.</b> 목록에서만 빼면 id 를 아는 사람에게는
 * 상세가 그대로 열리고, 그 화면에는 장바구니와 구매 버튼이 있다.
 */
@Service
@RequiredArgsConstructor
public class PublicProductCatalogService {

    private static final String UNKNOWN_BRAND = "알 수 없는 브랜드";

    private final ProductModelRepository productModelRepository;
    private final ProductItemRepository productItemRepository;
    private final ProductModelSearchQueryRepository productModelSearchQueryRepository;
    private final ProductStockQueryRepository productStockQueryRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final UserRepository userRepository;
    private final ProductImageService productImageService;

    @Transactional(readOnly = true)
    public PageResponse<PublicProductModelResponse> search(
            PublicProductModelSearchCondition condition) {
        var onSaleCondition = condition.toOnSaleCondition();
        Pageable pageable = PageRequest.of(
                onSaleCondition.pageOrDefault(), onSaleCondition.sizeOrDefault());
        Page<ProductModel> models =
                productModelSearchQueryRepository.searchConsumerVisible(onSaleCondition, pageable);
        List<Long> modelIds = models.getContent().stream().map(ProductModel::getId).toList();
        Map<Long, Long> stocks =
                productStockQueryRepository.countInStockByProductModelIds(modelIds);
        Map<Long, String> brandNames = brandNames(models.getContent());

        return PageResponse.from(models.map(model -> PublicProductModelResponse.of(
                model,
                brandNames.getOrDefault(model.getBrandId(), UNKNOWN_BRAND),
                stocks.getOrDefault(model.getId(), 0L))));
    }

    @Transactional(readOnly = true)
    public PublicProductModelResponse findDetail(Long productModelId) {
        ProductModel model = findOnSaleModel(productModelId);
        long stock = productItemRepository.countByProductModelIdAndStatus(
                productModelId, ProductItemStatus.IN_STOCK);
        String brandName = brandNames(List.of(model))
                .getOrDefault(model.getBrandId(), UNKNOWN_BRAND);
        return PublicProductModelResponse.of(model, brandName, stock);
    }

    @Transactional(readOnly = true)
    public FileContent readThumbnail(Long productModelId) {
        findOnSaleModel(productModelId);
        return productImageService.readThumbnail(productModelId);
    }

    /**
     * 공개할 수 있는 상품만 찾는다.
     *
     * <p>소비자에게 감추는 분류는 <b>403 이 아니라 404 다.</b> 403 은 "그 id 의 상품은
     * 있는데 볼 수 없다" 를 알려주는 답이고, 숨긴 상품의 존재를 확인해 주는 셈이 된다.
     * 소비자 쪽에서 그 상품은 없는 것과 같아야 한다.
     */
    private ProductModel findOnSaleModel(Long productModelId) {
        ProductModel model = productModelRepository
                .findByIdAndStatus(productModelId, ProductModelStatus.ON_SALE)
                .orElseThrow(() ->
                        new BusinessException(ProductErrorCode.PRODUCT_MODEL_NOT_FOUND));
        if (!model.getCategory().isConsumerVisible()) {
            throw new BusinessException(ProductErrorCode.PRODUCT_MODEL_NOT_FOUND);
        }
        return model;
    }

    private Map<Long, String> brandNames(List<ProductModel> models) {
        List<Long> brandIds = models.stream()
                .map(ProductModel::getBrandId)
                .distinct()
                .toList();
        if (brandIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, String> names = new LinkedHashMap<>();
        for (User user : userRepository.findAllById(brandIds)) {
            names.put(user.getId(), user.getName());
        }
        for (CompanyProfile profile : companyProfileRepository.findAllById(brandIds)) {
            names.put(profile.getUserId(), profile.getCompanyName());
        }
        return names;
    }
}
