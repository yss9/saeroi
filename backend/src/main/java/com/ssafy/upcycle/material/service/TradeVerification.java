package com.ssafy.upcycle.material.service;

/**
 * 소재가 거래로 검증되었는지 묻는 창구.
 *
 * <p><b>이 인터페이스를 material 이 소유하는 것이 핵심이다.</b> 판정에 필요한 사실은
 * trade 도메인에 있지만, material 이 trade 의 리포지토리를 직접 끌어다 쓰면 순환 참조가
 * 된다 — trade 는 이미 material 을 여러 곳에서 참조한다
 * ({@code TradeReceiptService} 의 잔량·잠금, {@code ListingCommandService} 의 잠금 해제).
 *
 * <p>필요한 질문만 여기 선언하고 구현을 trade 에 두면 참조 방향이 한쪽으로 유지된다.
 * Processing·Production 이 검증 단계에 합류할 때도 같은 방식으로 창구를 하나씩 늘린다.
 */
public interface TradeVerification {

    /**
     * 이 소재에 구매자가 서명한 거래가 하나라도 있는가.
     *
     * <p>서명 시각이 남았다는 것은 정책 09.1 의 확정 조건 다섯 가지가 모두 충족되었다는
     * 뜻이다 — 결제·배송·수령 확인·서명·보유량 이전이 한 트랜잭션에서 함께 처리되고,
     * 하나라도 어긋나면 시각이 기록되지 않는다.
     */
    boolean hasBuyerSignedTrade(Long materialId);
}
