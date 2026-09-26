package com.ssafy.upcycle.product.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.product.ProductErrorCode;
import com.ssafy.upcycle.product.dto.ProductModelResponse;
import com.ssafy.upcycle.product.dto.ProductModelSearchCondition;
import com.ssafy.upcycle.product.entity.ProductItemStatus;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.product.repository.ProductModelSearchQueryRepository;
import com.ssafy.upcycle.product.repository.ProductStockQueryRepository;
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
 * 제품 조회.
 *
 * <p><b>재고는 저장된 값이 아니라 개체를 세어 구한다.</b> {@code product_model} 에 재고
 * 컬럼이 없는 것이 이 도메인의 규칙이고, 등록·판매 시작이 쓰는 계산과 같은 것을 쓴다 —
 * 조회만 다른 방식으로 세면 화면 재고와 판매 가능 여부가 갈라진다.
 */
@Service
@RequiredArgsConstructor
public class ProductModelQueryService {

    private final ProductModelRepository productModelRepository;
    private final ProductItemRepository productItemRepository;
    private final ProductStockQueryRepository productStockQueryRepository;
    private final ProductModelSearchQueryRepository productModelSearchQueryRepository;

    /**
     * 제품 상세.
     *
     * <p><b>역할을 보지 않는다.</b> 인증만 있으면 누구나 같은 값을 본다 — 제품 정보는
     * 마켓에 걸리는 공개 정보이고, 소재 상세가 추적성을 이유로 같은 판단을 한다.
     *
     * <p>{@code DRAFT} 도 숨기지 않는다. 마켓 목록에 뜨지 않을 뿐이고, 숨기면 브랜드가
     * 등록 직후 자기 제품을 확인할 방법이 없어진다.
     */
    @Transactional(readOnly = true)
    public ProductModelResponse findDetail(Long productModelId) {
        ProductModel model = productModelRepository.findById(productModelId)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_MODEL_NOT_FOUND));
        long stock = productItemRepository.countByProductModelIdAndStatus(
                productModelId, ProductItemStatus.IN_STOCK);
        return ProductModelResponse.of(model, stock);
    }

    /**
     * 마켓 제품 목록.
     *
     * <p>기본이 {@code ON_SALE} 이다 — 준비 중인 제품이 섞이면 마켓의 의미가 흐려진다.
     * 브랜드 본인 화면은 {@link #findMyProductModels} 가 따로 있고 그쪽이 {@code DRAFT} 를
     * 포함한다.
     *
     * <p>재고는 <b>이 페이지의 제품들만</b> 한 번에 센다. 모델마다 세면 그게 N+1 이고,
     * 목록 질의에 집계를 붙이면 count 가 제품이 아니라 개체를 세게 된다
     * ({@code ProductModelSearchQueryRepository} 주석).
     *
     * <p><b>재고 0 인 제품도 그대로 내려간다.</b> 다 팔린 제품을 {@code SOLD_OUT} 으로
     * 바꾸는 주문 처리가 아직 없어서, 여기서 숨기면 브랜드는 자기 제품이 마켓에서 사라진
     * 이유를 알 수 없다. 화면이 {@code stockQuantity} 로 품절을 표시하는 편이 정직하다.
     */
    @Transactional(readOnly = true)
    public PageResponse<ProductModelResponse> search(ProductModelSearchCondition condition) {
        Pageable pageable = PageRequest.of(condition.pageOrDefault(), condition.sizeOrDefault());
        Page<ProductModel> models = productModelSearchQueryRepository.search(condition, pageable);
        Map<Long, Long> stocks = productStockQueryRepository.countInStockByProductModelIds(
                models.getContent().stream().map(ProductModel::getId).toList());
        return PageResponse.from(models.map(
                model -> ProductModelResponse.of(model, stocks.getOrDefault(model.getId(), 0L))));
    }

    /**
     * 브랜드 본인의 제품 목록. <b>{@code DRAFT} 를 포함한다.</b>
     *
     * <p>마켓 목록과 다른 점이 그것이다. 브랜드는 등록해 둔 제품을 모아 보고 거기서 판매
     * 시작을 눌러야 하는데, 준비 중인 제품이 안 보이면 그 버튼을 놓을 자리가 없다.
     *
     * <p>브랜드는 JWT {@code sub} 에서 온다 — 파라미터로 받으면 남의 제품 목록을 들여다볼
     * 수 있고, 거기에는 아직 공개하지 않은 {@code DRAFT} 가 들어 있다.
     *
     * <p>페이지를 나누지 않는다. 한 브랜드가 가진 제품 종류는 화면 하나에 들어가는 수이고,
     * 소재의 {@code /me} 도 같은 판단을 한다. 마켓 목록은 전 브랜드를 훑으므로 다르다.
     */
    @Transactional(readOnly = true)
    public List<ProductModelResponse> findMyProductModels(Long brandId) {
        List<ProductModel> models = productModelRepository.findAllByBrandId(brandId);
        // 모델마다 카운트 질의를 부르면 그게 정확히 N+1 이다. 한 번에 세어 붙인다.
        Map<Long, Long> stocks = productStockQueryRepository.countInStockByProductModelIds(
                models.stream().map(ProductModel::getId).toList());
        return models.stream()
                // 개체가 하나도 없는 모델은 집계 결과에 없다. 등록 직후가 그 상태다.
                .map(model -> ProductModelResponse.of(
                        model, stocks.getOrDefault(model.getId(), 0L)))
                .toList();
    }
}
