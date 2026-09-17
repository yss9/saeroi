package com.ssafy.upcycle.product.dto;

/**
 * 주문 묶음의 결제 준비 결과. <b>아직 청구되지 않았다.</b>
 *
 * <p>{@code redirectUrl} 로 사용자를 보내면 결제창이 열린다. 비어 있으면 결제 키가 설정되지
 * 않아 Mock 게이트웨이가 붙은 것이고({@code live} 가 {@code false}), 화면은 이동 없이 바로
 * 승인 단계로 갈 수 있다 — 단일 주문과 같은 판단이다.
 *
 * @param totalPrice 묶음 전체 금액. 브랜드별 주문 금액의 합이고 결제창에 청구되는 값이다
 */
public record ProductOrderGroupPaymentReadyResponse(
        Long orderGroupId, String tid, String redirectUrl, long totalPrice, boolean live) {
}
