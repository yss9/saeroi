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
 * <p>SHOP 목록과 찜 목록이 <b>같은 카드</b>를 보여 주지만 <b>담는 기준이 정반대</b>다. SHOP 은
 * 공개 조건에 맞지 않는 상품을 아예 빼고, 찜은 맞지 않아도 남긴다 — 사용자가 직접 담은
 * 목록이라 서버가 지울 수 없다.
 *
 * <p>그래서 공개 목록 조회를 그대로 부르면 안 된다. 그쪽은 {@code ON_SALE} 과 감춘 분류를
 * 조건으로 들고 있어서, 찜해 둔 상품이 단종되는 순간 <b>목록에서 소리 없이 사라진다.</b>
 * 조립하는 일만 여기로 꺼내고 <b>거르는 조건은 부르는 쪽에 남긴다.</b>
 *
 * <h2>질의 수는 목록 크기와 무관하다</h2>
 *
 * <p>재고·갤러리·브랜드명을 각각 한 번씩 모아 온다 — 상품마다 부르면 그게 N+1 이다. 페이지에
 * 30개가 실리든 12개가 실리든 질의 수는 같다.
 *
 * <h2>이미지 주소가 둘인 이유</h2>
 *
 * <p>공개 이미지 경로는 판매 중인 상품만 열려 있다. 찜 목록에는 단종된 상품이 남을 수
 * 있으므로 그 화면은 인증 경로를 써야 사진이 깨지지 않는다. 어느 쪽을 쓸지는 화면이 알고
 * 있으므로 부르는 쪽이 고른다.
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

    /** 비로그인 화면이 쓸 카드. 이미지 주소가 공개 경로다. */
    @Transactional(readOnly = true)
    public Map<Long, PublicProductModelResponse> assemblePublic(List<ProductModel> models) {
        return assemble(models, false);
    }

    /**
     * 로그인한 사용자만 보는 화면이 쓸 카드. 이미지 주소가 인증 경로다.
     *
     * <p>판매 중이 아닌 상품도 사진이 열린다 — 찜 목록처럼 공개 조건을 벗어난 상품이 남는
     * 화면이 쓴다.
     */
    @Transactional(readOnly = true)
    public Map<Long, PublicProductModelResponse> assembleAuthenticated(List<ProductModel> models) {
        return assemble(models, true);
    }

    /**
     * 상품 id 로 찾아 쓰도록 map 으로 돌려준다.
     *
     * <p>부르는 쪽의 순서가 제각각이기 때문이다 — 목록은 페이지 순서, 찜은 담은 순서다.
     * 순서를 여기서 정해 주면 그 중 한쪽이 자기 순서를 다시 맞춰야 한다.
     */
    private Map<Long, PublicProductModelResponse> assemble(
            List<ProductModel> models, boolean authenticatedImages) {
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
                    authenticatedImages
                            ? PublicProductModelResponse.ofAuthenticated(
                                    model, brandName, stock, gallery)
                            : PublicProductModelResponse.of(model, brandName, stock, gallery));
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
