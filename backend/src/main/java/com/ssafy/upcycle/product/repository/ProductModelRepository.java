package com.ssafy.upcycle.product.repository;

import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 조회 경로는 schema.sql 이 선언한 인덱스를 따른다 — {@code (brand_id, status)} 다.
 *
 * <p>소비자용 제품 목록(카테고리·가격 필터, 페이징)은 여기 두지 않는다. 재고까지 함께
 * 세어야 해서 {@code product_item} 집계가 붙고, 모델마다 개체를 다시 읽으면 N+1 이 된다.
 * 목록 API 티켓에서 QueryDSL 로 한 번에 짠다.
 */
public interface ProductModelRepository extends JpaRepository<ProductModel, Long> {

    List<ProductModel> findAllByBrandIdAndStatus(Long brandId, ProductModelStatus status);

    List<ProductModel> findAllByBrandId(Long brandId);
}
