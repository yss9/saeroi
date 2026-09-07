package com.ssafy.upcycle.trade.dto;

import com.ssafy.upcycle.trade.entity.TradeStatus;

/**
 * 내 거래 목록의 조건.
 *
 * <p><b>사용자 id 가 없다.</b> JWT 에서 온 값으로 강제하기 때문이며, 여기에 두면 남의 거래
 * 목록을 조회할 수 있다.
 *
 * <p>{@link ListingSearchCondition} 과 달리 정렬 옵션이 없다. 거래는 항상 최신순이다 —
 * 내가 방금 한 거래가 맨 위에 있어야 하고, 다른 순서를 원할 이유가 잘 상상되지 않는다.
 * 필요해지면 그때 넣는다.
 */
public record TradeSearchCondition(TradeSide side, TradeStatus status, Integer page, Integer size) {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    /** 어느 쪽 거래인가. */
    public enum TradeSide {
        /** 내가 산 것. 기본값이다. */
        BUY,
        /** 내가 판 것. */
        SELL
    }

    /**
     * 기본은 구매 쪽이다. 브랜드가 결제 후 상태를 확인하러 오는 것이 이 화면의 주된 쓰임이고,
     * 판매자는 발송 대기 건을 보러 {@code SELL} 로 들어온다.
     */
    public boolean isSellSide() {
        return side == TradeSide.SELL;
    }

    public int pageOrDefault() {
        return page == null || page < 0 ? 0 : page;
    }

    public int sizeOrDefault() {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
