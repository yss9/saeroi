package com.ssafy.upcycle.trade.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.trade.TradeErrorCode;
import com.ssafy.upcycle.trade.dto.PageResponse;
import com.ssafy.upcycle.trade.dto.TradeResponse;
import com.ssafy.upcycle.trade.dto.TradeSearchCondition;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.repository.TradeSearchRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 거래 목록과 거래 상세.
 *
 * <p><b>거래는 공개 정보가 아니다.</b> 소재 상세와 달리 금액과 양쪽 지갑 주소가 들어 있어서,
 * 목록은 항상 "내" 거래이고 상세는 당사자만 볼 수 있다. 그래서 조회 조건에 사용자 id 를
 * 받는 것이 아니라 <b>JWT 에서 온 id 로 강제</b>한다 — 파라미터로 받으면 남의 거래 목록을
 * 볼 수 있다.
 */
@Service
@RequiredArgsConstructor
public class TradeQueryService {

    private final TradeSearchRepository tradeSearchRepository;
    private final UserRepository userRepository;

    /**
     * 내 거래 목록. 판매 쪽인지 구매 쪽인지를 {@code side} 로 고른다.
     *
     * <p>기본은 구매({@code BUY})다. 브랜드가 결제 후 상태를 확인하러 오는 것이 이 화면의
     * 주된 쓰임이기 때문이다. 판매자는 {@code side=SELL} 로 발송 대기 건을 본다.
     */
    @Transactional(readOnly = true)
    public PageResponse<TradeResponse> findMyTrades(Long userId, TradeSearchCondition condition) {
        User me = userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(TradeErrorCode.TRADE_BUYER_NOT_FOUND));

        // 정렬은 쿼리의 order by 가 정한다. Pageable 로 넘기면 별칭 없는 프로퍼티가
        // 조인 쿼리에 붙어 어느 테이블인지 모호해진다.
        Pageable pageable = PageRequest.of(condition.pageOrDefault(), condition.sizeOrDefault());
        boolean meIsSeller = condition.isSellSide();

        Page<Object[]> rows = meIsSeller
                ? tradeSearchRepository.findSoldBy(userId, condition.status(), pageable)
                : tradeSearchRepository.findBoughtBy(userId, condition.status(), pageable);

        return PageResponse.from(
                rows.map(row -> TradeResponseAssembler.fromListRow(row, me, meIsSeller)));
    }

    /** 거래 상세. 당사자(판매자·구매자)만 볼 수 있다. */
    @Transactional(readOnly = true)
    public TradeResponse findDetail(Long userId, Long tradeId) {
        List<Object[]> rows = tradeSearchRepository.findDetailById(tradeId);
        if (rows.isEmpty()) {
            throw new BusinessException(TradeErrorCode.TRADE_NOT_FOUND);
        }
        Object[] row = rows.get(0);
        MaterialTrade trade = (MaterialTrade) row[0];
        if (!trade.getSellerId().equals(userId) && !trade.getBuyerId().equals(userId)) {
            throw new BusinessException(TradeErrorCode.TRADE_NOT_PARTICIPANT);
        }
        return TradeResponseAssembler.fromDetailRow(row);
    }
}
