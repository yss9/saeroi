package com.ssafy.upcycle.trade.dto;

/**
 * 결제 준비 결과.
 *
 * <p>{@code redirectUrl} 이 비어 있으면 <b>보낼 결제창이 없다</b>는 뜻이다. 카카오페이 키가
 * 설정되지 않아 Mock 게이트웨이가 붙은 경우이며, 화면은 이동하지 않고 시연 안내를 띄운 뒤
 * 승인 단계로 바로 넘어갈 수 있다.
 *
 * <p>{@code live} 는 그 판단을 화면이 문자열 비교로 하지 않게 하려고 따로 둔다.
 * "빈 문자열이면 Mock" 같은 규칙은 언젠가 한쪽에서만 바뀐다.
 *
 * <p>{@code tid} 를 내려보내는 것은 화면이 다시 보내라는 뜻이 아니다 — 승인 요청에는
 * {@code pgToken} 만 필요하고 tid 는 서버가 거래에서 읽는다. 결제 문의 때 사용자가 이 번호를
 * 말할 수 있어야 해서 노출한다.
 */
public record PaymentReadyResponse(
        Long tradeId, String tid, String redirectUrl, long totalPrice, boolean live) {
}
