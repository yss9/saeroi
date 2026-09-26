package com.ssafy.upcycle.product.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.trade.TradeErrorCode;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import com.ssafy.upcycle.trade.payment.PaymentApproval;
import com.ssafy.upcycle.trade.payment.PaymentApproveCommand;
import com.ssafy.upcycle.trade.payment.PaymentCancelCommand;
import com.ssafy.upcycle.trade.payment.PaymentGateway;
import com.ssafy.upcycle.trade.payment.PaymentReady;
import com.ssafy.upcycle.trade.payment.PaymentReadyCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 결제 게이트웨이를 부르고 금액을 대조하는 일.
 *
 * <h2>왜 꺼냈는가</h2>
 *
 * <p>결제의 단위가 둘이 됐다 — 단일 주문은 주문 자신이, 장바구니 주문은 주문 묶음이 결제한다.
 * 두 경로가 각자 게이트웨이를 부르면 <b>금액 대조 같은 검사가 한쪽에만 있는 상태</b>가
 * 만들어진다. 나뉘어야 하는 것은 "무엇이 결제의 단위인가" 뿐이고, 부르는 방법과 검사는
 * 하나여야 한다.
 *
 * <p>옮긴 것뿐이고 동작은 그대로다. {@code ProductOrderPaymentService} 가 하던 순서·검사·에러
 * 코드를 그대로 쓴다.
 *
 * <p><b>PG 가 붙었는지 여기서는 모른다.</b> 키가 없으면 {@code MockPaymentGateway} 가 대신
 * 붙지만 금액 대조는 두 경우 모두 똑같이 실행한다. Mock 에서만 통과하는 경로가 생기면
 * 실연동 때 그게 터진다.
 */
@Component
@RequiredArgsConstructor
public class ProductPaymentCoordinator {

    private final PaymentGateway paymentGateway;

    /**
     * 결제를 준비하고 결제창 주소를 받는다. <b>돈은 아직 움직이지 않는다.</b>
     *
     * @param code 화면과 PG 가 함께 쓰는 식별자. 단일 주문은 주문번호, 묶음은 묶음 번호다
     * @param itemName 결제창에 보일 이름. 번호만 보여주면 사용자가 무엇을 사는지 알 수 없다
     */
    public PaymentReady ready(
            String code, Long buyerId, String itemName, long amount, String returnUrl) {
        return paymentGateway.ready(new PaymentReadyCommand(
                code, String.valueOf(buyerId), itemName, amount, returnUrl));
    }

    /**
     * 승인한다. <b>이 시점에 실제로 청구된다.</b>
     *
     * <p><b>승인 금액을 우리가 계산한 금액과 대조한다.</b> PG 응답을 그대로 믿고 기록하면
     * 금액이 어긋난 승인이 정상 결제로 남는다. 대조에 실패하면 예외를 던져 트랜잭션을
     * 롤백한다 — 다만 <b>이미 청구는 일어난 뒤</b>라 사람이 개입해 환불해야 한다. 상태를
     * 바꾸지 않고 실패로 남기는 편이 낫다.
     */
    public PaymentApproval approve(
            String tid, String code, Long buyerId, String pgToken, long expectedAmount) {
        if (tid == null || tid.isBlank()) {
            throw new BusinessException(TradeErrorCode.PAYMENT_NOT_PREPARED);
        }
        PaymentApproval approval = paymentGateway.approve(new PaymentApproveCommand(
                tid, code, String.valueOf(buyerId), pgToken, expectedAmount));
        if (approval.approvedAmount() != expectedAmount) {
            throw new BusinessException(TradeErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
        return approval;
    }

    /**
     * 실제로 청구된 결제만 환불한다.
     *
     * <p><b>{@code MOCK} 으로 결제된 것은 건너뛴다.</b> 지금 붙어 있는 게이트웨이가 아니라
     * <b>그때 실제로 청구한 쪽</b>을 봐야 한다 — 이 둘은 다를 수 있다. 결제 키가 없던
     * 환경에서 결제한 건을 키가 생긴 뒤에 취소하면, 카카오페이에 존재하지 않는 거래의 환불을
     * 요청하게 된다. 그러면 환불이 거절되고 예외가 트랜잭션을 롤백해 <b>그 건은 영원히
     * 취소할 수 없게 된다.</b>
     */
    public void refund(PaymentMethod chargedWith, String tid, long amount) {
        if (chargedWith == PaymentMethod.MOCK) {
            return;
        }
        paymentGateway.cancel(new PaymentCancelCommand(tid, amount));
    }

    /** 실제 PG 가 붙어 있는지. 승인 시점에 어느 수단으로 청구됐는지 기록할 때 쓴다. */
    public PaymentMethod chargedMethod() {
        return paymentGateway.isLive() ? PaymentMethod.KAKAO_PAY : PaymentMethod.MOCK;
    }

    public boolean isLive() {
        return paymentGateway.isLive();
    }
}
