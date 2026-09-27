package com.ssafy.upcycle.trade.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.service.MaterialProcessHistoryResolver;
import com.ssafy.upcycle.processing.entity.ProcessType;
import com.ssafy.upcycle.trade.TradeErrorCode;
import com.ssafy.upcycle.trade.dto.ListingResponse;
import com.ssafy.upcycle.trade.dto.ListingSearchCondition;
import com.ssafy.upcycle.trade.dto.PageResponse;
import com.ssafy.upcycle.trade.repository.ListingSearchQueryRepository;
import java.util.List;
import java.util.Map;
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
 * <p>조회는 {@link ListingSearchQueryRepository}(QueryDSL)에 있다. 이 클래스는 <b>조건을
 * 넘기고 응답 봉투를 씌우는 일만</b> 한다 — 정렬 규칙이나 조건 조립이 여기 섞이면
 * 쿼리와 두 곳에 흩어진다.
 *
 * <p>{@code readOnly = true} 다. 조회만 하므로 Hibernate 의 dirty checking 을 끄고
 * 커넥션을 읽기 전용으로 잡는다.
 */
@Service
@RequiredArgsConstructor
public class ListingQueryService {

    private final ListingSearchQueryRepository listingSearchQueryRepository;
    private final MaterialProcessHistoryResolver processHistoryResolver;

    /** 조건에 맞는 판매 글을 페이지로 읽는다. */
    @Transactional(readOnly = true)
    public PageResponse<ListingResponse> search(ListingSearchCondition condition) {
        Pageable pageable =
                PageRequest.of(condition.pageOrDefault(), condition.sizeOrDefault());
        Page<ListingResponse> page = listingSearchQueryRepository.search(condition, pageable);
        // 페이지 전체를 한 번에 푼다 — 카드마다 혈통을 따로 거슬러 오르면 N+1 이다.
        Map<Long, List<ProcessType>> processTypes = processHistoryResolver.resolveAll(
                page.getContent().stream().map(ListingResponse::materialId).toList());
        return PageResponse.from(page.map(listing -> listing.withCompletedProcessTypes(
                processTypes.getOrDefault(listing.materialId(), List.of()))));
    }

    /**
     * 판매 글 상세. 소재와 판매자를 함께 읽어 화면이 세 번 호출하지 않게 한다.
     *
     * <p>없으면 404 다. {@code null} 을 그대로 흘려보내면 화면이 빈 카드를 그린다.
     */
    @Transactional(readOnly = true)
    public ListingResponse findDetail(Long listingId) {
        ListingResponse listing = listingSearchQueryRepository.findDetail(listingId);
        if (listing == null) {
            throw new BusinessException(TradeErrorCode.LISTING_NOT_FOUND);
        }
        return listing.withCompletedProcessTypes(processHistoryResolver.resolve(listing.materialId()));
    }
}
