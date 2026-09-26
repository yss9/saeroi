package com.ssafy.upcycle.user;

import com.ssafy.upcycle.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 장바구니 실패 사유. */
public enum CartErrorCode implements ErrorCode {

    /**
     * 소비자가 아닌 계정이 장바구니를 쓰려는 경우.
     *
     * <p>토큰의 역할 클레임이 아니라 DB 의 현재 역할로 판단한다 — 토큰은 발급 시점의
     * 스냅샷이라 역할이 바뀐 계정이 만료 전까지 계속 통과한다. 찜·제품 주문과 같은 검사다.
     */
    CART_ROLE_NOT_ALLOWED(
            HttpStatus.FORBIDDEN, "CART_ROLE_NOT_ALLOWED", "소비자 계정만 장바구니를 쓸 수 있습니다."),

    /** 없는 상품 id 로 담으려는 경우. */
    CART_PRODUCT_NOT_FOUND(
            HttpStatus.NOT_FOUND, "CART_PRODUCT_NOT_FOUND", "상품을 찾을 수 없습니다."),

    /**
     * 담지 않은 상품의 수량을 바꾸려는 경우.
     *
     * <p>제거는 담겨 있지 않아도 성공이지만(결과 상태가 같다) 수량 변경은 다르다 — 바꿀
     * 대상이 없으면 요청이 이루려던 상태가 만들어지지 않는다.
     */
    CART_ITEM_NOT_FOUND(
            HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "장바구니에 담기지 않은 상품입니다."),

    /** 0·음수이거나 항목당 상한을 넘는 수량. */
    CART_QUANTITY_OUT_OF_RANGE(
            HttpStatus.BAD_REQUEST,
            "CART_QUANTITY_OUT_OF_RANGE",
            "수량은 1개 이상 99개 이하로 담을 수 있습니다."),

    /**
     * 남은 재고보다 많이 담으려는 경우.
     *
     * <p>형식은 맞지만 지금 상태로는 안 되는 요청이라 409 다 — 주문 품절
     * ({@code PRODUCT_ORDER_OUT_OF_STOCK})과 같은 성격이다.
     */
    CART_QUANTITY_EXCEEDS_STOCK(
            HttpStatus.CONFLICT, "CART_QUANTITY_EXCEEDS_STOCK", "남은 재고보다 많이 담을 수 없습니다."),

    /** 장바구니에 담을 수 있는 상품 종류를 넘긴 경우. */
    CART_FULL(
            HttpStatus.BAD_REQUEST, "CART_FULL", "장바구니에는 최대 50종까지 담을 수 있습니다."),

    /**
     * 같은 상품을 담는 요청이 동시에 들어와 유니크 제약에 걸린 경우.
     *
     * <p>{@code uk_consumer_cart_item_user_product} 가 같은 상품이 두 행이 되는 것을 막는다.
     * 앞선 요청이 이미 담았다는 뜻이라 다시 시도하면 수량이 누적된다.
     *
     * <p><b>드문 경우로 보지 않는다.</b> 담기 버튼 연타는 흔하고, 화면이 요청 중에 그 상품의
     * 담기를 막지 않으면 사용자가 이 응답을 보게 된다 — 상품별로 진행 중인 담기 요청을
     * 막아 주는 것이 전제다.
     */
    CART_CONCURRENT_UPDATE(
            HttpStatus.CONFLICT, "CART_CONCURRENT_UPDATE", "장바구니가 방금 바뀌었습니다. 다시 시도해 주세요."),

    /**
     * 주문하려는 항목 중 지금은 살 수 없는 것이 있는 경우.
     *
     * <p><b>되는 것만 주문하지 않는다.</b> 조용히 빼면 사용자가 화면에서 본 합계와 다른
     * 금액이 결제되고 그건 분쟁이 된다 — 재고 부족을 전체 실패로 다루는 것과 같은 판단이다.
     *
     * <p>어떤 상품이 왜 안 되는지는 응답 상세({@code UnavailableCartLine})에 실린다.
     */
    CART_CHECKOUT_ITEMS_UNAVAILABLE(
            HttpStatus.CONFLICT,
            "CART_CHECKOUT_ITEMS_UNAVAILABLE",
            "지금은 구매할 수 없는 상품이 있습니다."),

    /**
     * 화면이 보낸 예상 결제 금액이 서버가 계산한 금액과 다른 경우.
     *
     * <p><b>요청이 그 값을 보냈을 때만 나온다.</b> 담아 둔 사이에 값이 오른 것을 모르고
     * 결제되는 경로를 막는 안전장치이고, 보내지 않으면 현재가로 그냥 진행한다.
     */
    CART_CHECKOUT_PRICE_CHANGED(
            HttpStatus.CONFLICT,
            "CART_CHECKOUT_PRICE_CHANGED",
            "상품 가격이 변경되었습니다. 금액을 다시 확인해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    CartErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
