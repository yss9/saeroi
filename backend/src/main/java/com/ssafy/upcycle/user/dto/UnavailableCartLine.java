package com.ssafy.upcycle.user.dto;

/**
 * 지금은 살 수 없어 주문을 막은 항목.
 *
 * <p>조용히 빼고 나머지만 주문하면 사용자가 화면에서 본 합계와 다른 금액이 결제된다.
 * 그래서 전체를 실패시키되 <b>어떤 상품이 왜 안 되는지</b>를 함께 보낸다 — 이 값이 없으면
 * 화면은 무엇을 빼야 할지 말해 줄 수 없다.
 *
 * <p>재고 부족은 여기에 오지 않는다. 그쪽은 몇 개 남았는지까지 알려야 해서 주문 쪽이
 * 따로 답한다({@code PRODUCT_ORDER_ITEMS_OUT_OF_STOCK}).
 */
public record UnavailableCartLine(Long productModelId, String productName, Reason reason) {

    /** 왜 못 사는가. 화면이 항목에 붙일 문구를 고르는 근거다. */
    public enum Reason {

        /** 판매 전이거나 브랜드가 내린 상품. */
        NOT_ON_SALE,

        /** 소비자에게 감추는 분류로 바뀐 상품. SHOP 에서도 보이지 않는다. */
        HIDDEN_CATEGORY
    }
}
