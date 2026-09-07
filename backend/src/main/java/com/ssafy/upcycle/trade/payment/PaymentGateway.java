package com.ssafy.upcycle.trade.payment;

/**
 * 결제 대행사(PG) 호출 경계.
 *
 * <p><b>인터페이스를 두는 이유는 구현을 갈아끼우기 위해서가 아니다.</b> PG 는 하나(카카오페이)로
 * 정해져 있다. 이 경계가 필요한 진짜 이유는 <b>자격증명 없이도 전체 흐름이 돌아야 하기
 * 때문</b>이다 — 팀원 대부분은 카카오페이 개발자 키를 갖고 있지 않고, 시연 환경에도 없다.
 * 키가 없으면 {@code MockPaymentGateway} 가 붙어 결제창 없이 같은 상태 전이를 만든다.
 *
 * <p>그래서 {@code PaymentService} 는 어느 쪽이 붙었는지 알지 못한다. 금액 검증·상태 전이·
 * 수량 복구 같은 <b>우리 책임은 두 경우 모두 똑같이 실행된다.</b> Mock 경로에서만 통과하는
 * 검증이 생기면 그게 실제 결제에서 터진다.
 *
 * <p>카카오페이는 <b>2단계</b>다. {@link #ready}로 결제창 주소와 {@code tid} 를 받고,
 * 사용자가 그 창에서 인증한 뒤 돌려주는 {@code pgToken} 으로 {@link #approve} 한다.
 * <b>승인까지 끝나야 돈이 움직인다</b> — ready 만 하고 사용자가 창을 닫으면 아무 일도 없다.
 */
public interface PaymentGateway {

    /**
     * 결제를 준비하고 사용자를 보낼 결제창 주소를 받는다. <b>아직 돈은 움직이지 않는다.</b>
     *
     * @param command 주문 식별자와 금액. 금액은 원 단위 정수다
     */
    PaymentReady ready(PaymentReadyCommand command);

    /**
     * 결제를 승인한다. <b>이 시점에 실제로 청구된다.</b>
     *
     * <p>응답의 승인 금액을 호출부가 반드시 대조해야 한다 — PG 가 우리가 요청한 금액과 다른
     * 금액을 승인했을 가능성을 배제하지 않는다.
     */
    PaymentApproval approve(PaymentApproveCommand command);

    /**
     * 승인된 결제를 취소(환불)한다.
     *
     * <p>실패하면 예외를 던진다. 호출부는 트랜잭션을 롤백해 거래를 취소 상태로 두지 않아야
     * 한다 — <b>돈은 그대로인데 거래만 취소된 상태가 가장 나쁘다.</b>
     */
    void cancel(PaymentCancelCommand command);

    /** 지금 붙어 있는 구현이 실제 PG 인지. 화면에 "시연 결제" 안내를 띄울지 판단하는 데 쓴다. */
    boolean isLive();
}
