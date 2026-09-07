package com.ssafy.upcycle.trade.repository;

import com.ssafy.upcycle.trade.entity.ListingStatus;
import com.ssafy.upcycle.trade.entity.MaterialListing;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 조회 경로는 schema.sql 이 선언한 인덱스를 따른다 —
 * {@code (seller_id, status)} 와 {@code material_id} 다.
 */
public interface MaterialListingRepository extends JpaRepository<MaterialListing, Long> {

    List<MaterialListing> findAllBySellerIdAndStatus(Long sellerId, ListingStatus status);

    List<MaterialListing> findAllByMaterialIdAndStatus(Long materialId, ListingStatus status);
}
