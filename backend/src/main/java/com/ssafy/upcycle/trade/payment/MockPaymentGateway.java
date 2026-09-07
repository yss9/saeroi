package com.ssafy.upcycle.trade.payment;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 카카오페이 키가 없을 때 붙는 대역.
 *
 * <p>키를 가진 사람이 팀에 몇 없고 시연 환경에도 없다. 그렇다고 결제 단계를 건너뛰면
 * 거래가 {@code CREATED} 에서 멈춰 발송·수령까지 아무것도 눌러볼 수 없다.
 *
 * <p><b>이것은 "결제를 생략" 하는 것이 아니라 "PG 만 대신" 하는 것이다.</b> 금액 대조,
 * 상태 전이, 잠금·수량 처리는 {@code PaymentService} 가 하고 그 코드는 실제 PG 와 공유된다.
 * 여기서만 통과하는 경로가 없어야 실연동 때 새로 터지는 것이 없다.
 *
 * <p>{@code redirectUrl} 은 빈 문자열이다. 보낼 결제창이 없다는 뜻이며, 화면은 이 값이 비어
 * 있으면 이동하지 않고 "시연 결제" 안내를 띄운다.
 */
public class MockPaymentGateway implements PaymentGateway {

    /** 실제 tid 와 눈으로 구분되어야 한다. 로그나 DB 에서 헷갈리면 정산 대조가 꼬인다. */
    private static final String TID_PREFIX = "MOCK-";

    private static final int RANDOM_BYTES = 8;

    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public MockPaymentGateway(Clock clock) {
        this.clock = clock;
    }

    @Override
    public PaymentReady ready(PaymentReadyCommand command) {
        byte[] bytes = new byte[RANDOM_BYTES];
        random.nextBytes(bytes);
        return new PaymentReady(TID_PREFIX + HexFormat.of().formatHex(bytes), "");
    }

    /**
     * 항상 성공하고, 요청한 금액을 그대로 승인한 것으로 돌려준다.
     *
     * <p>그래서 Mock 경로에서는 금액 불일치가 발생하지 않는다. 검증 자체를 건너뛰는 것이
     * 아니라 <b>대조가 항상 통과하는 값</b>을 주는 것이고, 대조 코드는 그대로 실행된다.
     * 불일치 상황은 테스트가 따로 만들어 고정한다.
     */
    @Override
    public PaymentApproval approve(PaymentApproveCommand command) {
        return new PaymentApproval(command.tid(), command.expectedAmount(), LocalDateTime.now(clock));
    }

    /** 환불도 항상 성공한다. 되돌릴 실제 결제가 없다. */
    @Override
    public void cancel(PaymentCancelCommand command) {
        // 아무것도 하지 않는다. 실패 경로를 흉내 내지 않는 것은 의도적이다 —
        // 무작위로 실패하면 시연 중에 설명할 수 없는 오류가 난다.
    }

    @Override
    public boolean isLive() {
        return false;
    }
}
