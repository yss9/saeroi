package com.ssafy.upcycle.trade.entity;

/**
 * 판매 등록의 상태.
 *
 * <p>{@link #SOLD_OUT} 은 잔여 판매 수량이 0 이 된 상태다. 부분 구매를 지원하므로
 * 한 번의 거래로 바로 넘어가지 않고, 여러 건이 쌓여 잔량이 소진될 때 전환된다.
 */
public enum ListingStatus {

    ON_SALE,
    SOLD_OUT,
    CANCELED
}
