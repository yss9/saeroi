package com.ssafy.upcycle.user;

import com.ssafy.upcycle.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 찜 실패 사유.
 *
 * <p>여기 없는 것이 하나 있다 — <b>이미 찜한 상품을 다시 담는 경우.</b> 그것은 실패가 아니라
 * 성공이다({@code ConsumerWishlistService.add} 주석). 하트 버튼은 연타되고 네트워크 재시도도
 * 흔해서, 오류로 답하면 화면이 그 오류를 성공처럼 해석하는 코드를 따로 써야 한다.
 */
public enum WishlistErrorCode implements ErrorCode {

    /**
     * 소비자가 아닌 계정이 찜을 시도한 경우.
     *
     * <p>토큰의 역할 클레임이 아니라 DB 의 현재 역할로 판단한다 — 토큰은 발급 시점의
     * 스냅샷이라 역할이 바뀐 계정이 만료 전까지 계속 통과한다. 제품 주문이 하는 검사와 같다.
     */
    WISHLIST_ROLE_NOT_ALLOWED(
            HttpStatus.FORBIDDEN,
            "WISHLIST_ROLE_NOT_ALLOWED",
            "소비자 계정만 찜할 수 있습니다."),

    /** 없는 상품 id 로 찜을 시도한 경우. */
    WISHLIST_PRODUCT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "WISHLIST_PRODUCT_NOT_FOUND",
            "상품을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    WishlistErrorCode(HttpStatus status, String code, String message) {
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
