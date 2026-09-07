package com.ssafy.upcycle.trade.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.trade.TradeErrorCode;
import com.ssafy.upcycle.trade.dto.TradeResponse;
import com.ssafy.upcycle.trade.entity.MaterialListing;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.TradeShipping;
import com.ssafy.upcycle.trade.repository.TradeSearchRepository;
import com.ssafy.upcycle.user.entity.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 거래 응답을 만든다. <b>거래를 바꾼 직후에도 같은 모양을 내려보내기 위한 것이다.</b>
 *
 * <p>{@link TradeResponse} 는 소재 이름과 양쪽 이름을 담는데, 구매·결제·취소 서비스는
 * 그 값들을 손에 들고 있지 않다. 그렇다고 명령 응답만 필드가 빈 다른 모양으로 내려보내면
 * 화면이 "구매 직후에는 소재 이름이 없다" 는 예외 규칙을 알아야 한다.
 *
 * <p>그래서 쓰기가 끝난 뒤 조인 한 번으로 다시 읽는다. 같은 트랜잭션이라 Hibernate 가
 * 쿼리 전에 flush 하므로 방금 바꾼 값이 그대로 보인다. 단건이라 비용은 쿼리 하나다.
 */
@Component
@RequiredArgsConstructor
public class TradeResponseAssembler {

    private final TradeSearchRepository tradeSearchRepository;

    /**
     * 방금 만들거나 바꾼 거래를 응답으로.
     *
     * <p>조회 결과가 비면 거래는 있는데 소재나 판매 글이 없다는 뜻이라 데이터가 어긋난
     * 상태다. 조용히 빈 응답을 만들지 않고 실패시킨다.
     */
    public TradeResponse of(MaterialTrade trade) {
        List<Object[]> rows = tradeSearchRepository.findDetailById(trade.getId());
        if (rows.isEmpty()) {
            throw new BusinessException(TradeErrorCode.TRADE_NOT_FOUND);
        }
        return fromDetailRow(rows.get(0));
    }

    /**
     * {@code [MaterialTrade, Material, MaterialListing, 판매자, 구매자, TradeShipping]} 순서는
     * 쿼리의 select 절이 정한 계약이다. 쿼리를 고칠 때 이 메서드도 함께 본다.
     *
     * <p>마지막 자리는 {@code left join} 이라 {@code null} 일 수 있다 — 배송지를 함께 받기
     * 이전에 만들어진 거래가 그렇다.
     */
    public static TradeResponse fromDetailRow(Object[] row) {
        return TradeResponse.of(
                (MaterialTrade) row[0],
                (Material) row[1],
                (MaterialListing) row[2],
                (User) row[3],
                (User) row[4],
                (TradeShipping) row[5]);
    }

    /**
     * 목록 쿼리는 상대방 한 명만 조인한다 —
     * {@code [MaterialTrade, Material, MaterialListing, 상대방, TradeShipping]}.
     *
     * <p>내 이름은 이미 알고 있으므로 조인을 하나 줄인다. 목록은 행 수만큼 곱해지는 자리라
     * 조인 하나의 차이가 상세보다 크다.
     *
     * @param me 조회한 사람. 판매 목록이면 판매자 자리에, 구매 목록이면 구매자 자리에 들어간다
     */
    public static TradeResponse fromListRow(Object[] row, User me, boolean meIsSeller) {
        MaterialTrade trade = (MaterialTrade) row[0];
        User counterparty = (User) row[3];
        return TradeResponse.of(
                trade,
                (Material) row[1],
                (MaterialListing) row[2],
                meIsSeller ? me : counterparty,
                meIsSeller ? counterparty : me,
                (TradeShipping) row[4]);
    }
}
