package com.ssafy.upcycle.product.repository;

import com.ssafy.upcycle.product.entity.ProductImage;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 상품 갤러리 조회. {@code (product_model_id, sort_order)} 유니크 키가 두 질의를 모두 받친다.
 *
 * <p><b>정렬을 메서드 이름에 박아 둔다.</b> 갤러리는 순서가 곧 의미라, 정렬 없이 읽는
 * 경로를 열어 두면 어느 화면에서는 대표 사진이 세 번째로 나온다.
 */
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findAllByProductModelIdOrderBySortOrderAsc(Long productModelId);

    /**
     * 여러 제품의 갤러리를 한 번에 읽는다. 목록 화면이 쓴다.
     *
     * <p>제품마다 부르면 그게 정확히 N+1 이다 — 재고를 한 번에 세는 것
     * ({@link ProductStockQueryRepository}) 과 같은 이유로 묶는다.
     */
    List<ProductImage> findAllByProductModelIdInOrderByProductModelIdAscSortOrderAsc(
            Collection<Long> productModelIds);

    void deleteAllByProductModelId(Long productModelId);
}
