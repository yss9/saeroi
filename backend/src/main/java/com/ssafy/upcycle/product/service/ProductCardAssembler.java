package com.ssafy.upcycle.product.service;

import com.ssafy.upcycle.product.dto.PublicProductModelResponse;
import com.ssafy.upcycle.product.entity.ProductImage;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.repository.ProductStockQueryRepository;
import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 행에 브랜드명·재고·갤러리를 붙여 상품 카드 응답을 만든다.
 *
 * <h2>왜 따로 있는가</h2>
 *
 * <p>브랜드명·재고·갤러리를 붙여 카드를 만드는 일과 <b>무엇을 담을지 고르는 일</b>은 다른
 * 관심사다. 공개 목록은 {@code ON_SALE} 과 감춘 분류를 조건으로 들고 있는데, 같은 카드를
 * 다른 기준으로 모아야 하는 화면이 생기면 그 조건까지 함께 딸려 온다.
 *
 * <p>그래서 <b>조립하는 일만 여기로 꺼내고 거르는 조건은 부르는 쪽에 남긴다.</b> 지금은
 * 공개 목록 하나가 쓰지만, 조건이 다른 화면이 붙어도 조립을 다시 만들지 않는다.
 *
 * <h2>질의 수는 목록 크기와 무관하다</h2>
 *
 * <p>재고·갤러리·브랜드명을 각각 한 번씩 모아 온다 — 상품마다 부르면 그게 N+1 이다. 페이지에
 * 30개가 실리든 12개가 실리든 질의 수는 같다.
 */
@Component
@RequiredArgsConstructor
public class ProductCardAssembler {

    /** 회사명도 사용자 이름도 없는 경우. */
    private static final String UNKNOWN_BRAND = "알 수 없는 브랜드";

    private final ProductStockQueryRepository productStockQueryRepository;
    private final ProductModelImageService productModelImageService;
    private final CompanyProfileRepository companyProfileRepository;
    private final UserRepository userRepository;

    /**
     * 비로그인 화면이 쓸 카드. <b>상품 id 로 찾아 쓰도록 map 으로 돌려준다.</b>
     *
     * <p>부르는 쪽의 순서가 제각각이기 때문이다 — 목록은 페이지 순서로 그리고, 다른 화면은
     * 자기 순서를 갖는다. 순서를 여기서 정해 주면 그 중 한쪽이 다시 맞춰야 한다.
     */
    @Transactional(readOnly = true)
    public Map<Long, PublicProductModelResponse> assemblePublic(List<ProductModel> models) {
        if (models.isEmpty()) {
            return Map.of();
        }
        List<Long> modelIds = models.stream().map(ProductModel::getId).toList();
        Map<Long, Long> stocks =
                productStockQueryRepository.countInStockByProductModelIds(modelIds);
        Map<Long, List<ProductImage>> galleries =
                productModelImageService.findGalleriesOf(modelIds);
        Map<Long, String> brandNames = brandNames(models);

        Map<Long, PublicProductModelResponse> cards = new LinkedHashMap<>();
        for (ProductModel model : models) {
            String brandName = brandNames.getOrDefault(model.getBrandId(), UNKNOWN_BRAND);
            long stock = stocks.getOrDefault(model.getId(), 0L);
            List<ProductImage> gallery = galleries.getOrDefault(model.getId(), List.of());
            cards.put(
                    model.getId(),
                    PublicProductModelResponse.of(model, brandName, stock, gallery));
        }
        return cards;
    }

    /**
     * 화면에 보이는 브랜드명 — 회사명이 있으면 회사명, 없으면 사용자 이름.
     *
     * <p>브랜드 목록·브랜드명 검색이 쓰는 규칙과 같아야 한다. 여기서만 다르면 사용자가
     * 카드에서 본 이름으로 검색해도 걸리지 않는다.
     */
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
