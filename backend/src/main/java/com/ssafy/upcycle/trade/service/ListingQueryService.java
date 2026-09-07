package com.ssafy.upcycle.trade.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.trade.TradeErrorCode;
import com.ssafy.upcycle.trade.dto.ListingResponse;
import com.ssafy.upcycle.trade.dto.ListingSearchCondition;
import com.ssafy.upcycle.trade.dto.PageResponse;
import com.ssafy.upcycle.trade.entity.MaterialListing;
import com.ssafy.upcycle.trade.repository.ListingSearchRepository;
import com.ssafy.upcycle.user.entity.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marketplace 목록·검색·필터와 판매 글 상세.
 *
 * <p>Brand Market(전체 판매 글)과 Supplier Market(내 판매 글)이 <b>같은 메서드를 쓴다.</b>
 * 두 화면의 차이는 {@code sellerId} 조건 하나뿐이고 필터·정렬·페이지는 완전히 같다.
 * 메서드를 나누면 필터가 하나 늘 때마다 두 곳을 고쳐야 한다.
 *
 * <p>{@code readOnly = true} 다. 조회만 하므로 Hibernate 의 dirty checking 을 끄고
 * 커넥션을 읽기 전용으로 잡는다.
 */
@Service
@RequiredArgsConstructor
public class ListingQueryService {

    private final ListingSearchRepository listingSearchRepository;

    /**
     * 조건에 맞는 판매 글을 페이지로 읽는다.
     *
     * <p>결과가 {@code Object[]} 인 것은 쿼리가 판매 글과 소재를 함께 select 하기 때문이다.
     * 그 형태가 밖으로 새면 화면이 배열 인덱스에 의존하므로 여기서 바로 DTO 로 접는다.
     */
    @Transactional(readOnly = true)
    public PageResponse<ListingResponse> search(ListingSearchCondition condition) {
        Pageable pageable = PageRequest.of(
                condition.pageOrDefault(), condition.sizeOrDefault(), condition.toSort());

        Page<ListingResponse> page = listingSearchRepository
                .search(
                        condition.statusOrDefault(),
                        condition.sellerId(),
                        condition.materialType(),
                        condition.rootOrigin(),
                        condition.gradeOrNull(),
                        condition.keywordOrNull(),
                        condition.minPricePerKg(),
                        condition.maxPricePerKg(),
                        condition.minAvailableQuantity(),
                        pageable)
                .map(ListingQueryService::toResponse);

        return PageResponse.from(page);
    }

    /** 판매 글 상세. 소재를 함께 읽어 화면이 두 번 호출하지 않게 한다. */
    @Transactional(readOnly = true)
    public ListingResponse findDetail(Long listingId) {
        List<Object[]> rows = listingSearchRepository.findWithMaterial(listingId);
        if (rows.isEmpty()) {
            throw new BusinessException(TradeErrorCode.LISTING_NOT_FOUND);
        }
        return toResponse(rows.get(0));
    }

    /**
     * {@code [MaterialListing, Material, 판매자]} 순서는 쿼리의 select 절이 정한 계약이다.
     * 쿼리를 고칠 때 이 메서드도 함께 본다.
     */
    private static ListingResponse toResponse(Object[] row) {
        return ListingResponse.of((MaterialListing) row[0], (Material) row[1], (User) row[2]);
    }
}
