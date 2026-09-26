package com.ssafy.upcycle.product.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.common.storage.FileContent;
import com.ssafy.upcycle.product.ProductErrorCode;
import com.ssafy.upcycle.product.dto.PublicProductModelResponse;
import com.ssafy.upcycle.product.dto.PublicProductModelSearchCondition;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductItemStatus;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.product.repository.ProductModelSearchQueryRepository;
import com.ssafy.upcycle.trade.dto.PageResponse;
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

    private final ProductModelRepository productModelRepository;
    private final ProductModelSearchQueryRepository productModelSearchQueryRepository;
    private final ProductCardAssembler productCardAssembler;
    private final ProductImageService productImageService;
    private final ProductModelImageService productModelImageService;
    private final ProductItemRepository productItemRepository;
    private final ProductQrCodeGenerator productQrCodeGenerator;

    @Transactional(readOnly = true)
    public PageResponse<PublicProductModelResponse> search(
            PublicProductModelSearchCondition condition) {
        var onSaleCondition = condition.toOnSaleCondition();
        Pageable pageable = PageRequest.of(
                onSaleCondition.pageOrDefault(), onSaleCondition.sizeOrDefault());
        Page<ProductModel> models =
                productModelSearchQueryRepository.searchConsumerVisible(onSaleCondition, pageable);
        // 브랜드명·재고·갤러리를 붙이는 일은 찜 목록과 똑같다. 다른 것은 무엇을 담느냐뿐이라
        // 조립만 공유하고 거르는 조건은 위 질의에 남긴다 ({@link ProductCardAssembler}).
        Map<Long, PublicProductModelResponse> cards =
                productCardAssembler.assemblePublic(models.getContent());
        return PageResponse.from(models.map(model -> cards.get(model.getId())));
    }

    @Transactional(readOnly = true)
    public PublicProductModelResponse findDetail(Long productModelId) {
        ProductModel model = findOnSaleModel(productModelId);
        PublicProductModelResponse card =
                productCardAssembler.assemblePublic(List.of(model)).get(productModelId);
        return card.withJourneyUrl(sampleJourneyUrl(productModelId));
    }

    /**
     * 재고 개체 하나를 골라 그 QR Passport 주소를 예시로 보여 준다.
     *
     * <p>같은 배치의 개체는 소재 구성이 동일하므로 어느 것을 보여줘도 정보상 차이가 없다.
     * 재고가 없으면 {@code null} — 화면은 이 경우 버튼 자체를 숨긴다.
     *
     * <p>{@code productItemId} 는 응답에 담지 않는다 — 완성된 주소만 내려준다
     * ({@link PublicProductModelResponse} 클래스 설명 참고).
     */
    private String sampleJourneyUrl(Long productModelId) {
        return productItemRepository
                .findFirstByProductModelIdAndStatusOrderByIdAsc(
                        productModelId, ProductItemStatus.IN_STOCK)
                .map(ProductItem::getQrToken)
                .map(productQrCodeGenerator::urlFor)
                .orElse(null);
    }

    /**
     * 소비자에게 보여 줄 갤러리 사진 한 장의 바이트와 캐시 키(storageKey).
     *
     * <p>상세와 같은 관문을 지난다 — 판매 중이 아니거나 소비자에게 감추는 분류면 404 다.
     * 이미지만 열어 두면 목록에서 사라진 제품의 사진이 주소만으로 계속 나간다.
     */
    @Transactional(readOnly = true)
    public PublicImageContent readImage(Long productModelId, Long productImageId) {
        findOnSaleModel(productModelId);
        FileContent file = productModelImageService.readImage(productModelId, productImageId);
        // ProductImage의 storageKey는 readImage 호출 경로에서 꺼낼 수 없으므로
        // 간단히 productImageId를 storageKey로 사용 (ETag는 productModelId+imageId 조합이면 충분)
        return new PublicImageContent(file, String.valueOf(productImageId));
    }

    @Transactional(readOnly = true)
    public PublicImageContent readThumbnail(Long productModelId) {
        ProductModel model = findOnSaleModel(productModelId);
        FileContent file = productImageService.readThumbnail(productModelId);
        // ProductModel.thumbnailUrl이 storageKey
        return new PublicImageContent(file, model.getThumbnailUrl());
    }

    /**
     * 공개 이미지 응답용 — FileContent + storageKey(ETag 생성 용).
     */
    public record PublicImageContent(FileContent content, String storageKey) {}

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

}
