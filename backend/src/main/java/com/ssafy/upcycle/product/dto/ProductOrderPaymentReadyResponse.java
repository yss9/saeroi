package com.ssafy.upcycle.product.dto;

/**
 * 제품 주문의 결제 준비 결과.
 *
 * <p>소재 거래의 {@code PaymentReadyResponse} 와 모양이 같은데도 따로 두는 이유는 <b>첫
 * 필드 이름 하나</b> 때문이다. 그쪽은 {@code tradeId} 이고, 제품 주문에 그대로 쓰면 응답에
 * 있는 번호가 거래 번호처럼 보인다. 실제로는 주문 번호이고 결제·취소 경로가 받는 값도
 * 그것이라, 이름이 틀리면 화면이 그 값을 안 쓰거나 잘못 쓴다.
 *
 * <p>공유 타입의 필드를 중립적인 이름으로 바꾸는 방법도 있지만, 그러면 이미 붙어 있는 소재
 * 거래 화면이 함께 깨진다. 결제 <b>동작</b>은 {@code PaymentGateway} 가 공유하고 <b>응답
 * 이름</b>만 도메인이 갖는 편이 낫다.
 *
 * <p>{@code redirectUrl} 이 비어 있으면 <b>보낼 결제창이 없다</b>는 뜻이다. 카카오페이 키가
 * 설정되지 않아 Mock 게이트웨이가 붙은 경우이며, 화면은 이동하지 않고 시연 안내를 띄운 뒤
 * 승인 단계로 바로 넘어갈 수 있다. {@code live} 는 그 판단을 화면이 문자열 비교로 하지
 * 않게 하려고 따로 둔다 — "빈 문자열이면 Mock" 같은 규칙은 언젠가 한쪽에서만 바뀐다.
 *
 * <p>{@code tid} 를 내려보내는 것은 화면이 다시 보내라는 뜻이 아니다 — 승인 요청에는
 * {@code pgToken} 만 필요하고 tid 는 서버가 주문에서 읽는다. 결제 문의 때 사용자가 이 번호를
 * 말할 수 있어야 해서 노출한다.
 */
public record ProductOrderPaymentReadyResponse(
        Long orderId, String tid, String redirectUrl, long totalPrice, boolean live) {
}
