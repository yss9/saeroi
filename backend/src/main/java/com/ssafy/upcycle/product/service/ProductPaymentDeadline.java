package com.ssafy.upcycle.product.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 결제 마감을 화면이 쓸 수 있는 모양으로 옮긴다.
 *
 * <h2>왜 두 값을 함께 내보내는가</h2>
 *
 * <p><b>시각에는 오프셋을 붙인다.</b> DB 와 엔티티는 {@code LocalDateTime} 이라 그대로
 * 내보내면 {@code 2026-09-18T00:26:46} 처럼 시간대가 없는 문자열이 된다. 브라우저는 그것을
 * <b>자기 시간대</b>로 해석하므로, 서버와 다른 시간대에서 열면 마감이 몇 시간 어긋난다.
 * 이 값은 버튼을 누를 수 있는지를 가르는 값이라 해석이 흔들리면 안 된다.
 *
 * <p><b>남은 시간도 함께 보낸다.</b> 시각만 주면 화면이 "지금"을 자기 시계에서 읽어 빼야
 * 하는데, 그 시계가 틀어져 있으면 카운트다운이 통째로 어긋난다. 서버가 계산해 주면 시계
 * 오차와 무관해진다.
 *
 * <p>{@code orderedAt}·{@code paidAt} 같은 기존 시각은 이 변환을 쓰지 않는다. 그쪽은 화면에
 * 적히기만 하는 값이라 지금 모양으로 충분하고, 바꾸면 그게 진짜 계약 변경이다.
 *
 * <h2>지난 마감도 그대로 싣는다</h2>
 *
 * <p>결제가 끝났거나 마감이 지난 주문도 값을 비우지 않는다. 서버가 조건부로 {@code null} 로
 * 만들면 화면은 "왜 없는지" 를 상태에서 다시 추론해야 한다. 대신 남은 시간은 0 밑으로
 * 내려가지 않는다 — 음수는 화면이 그대로 표시할 수 없는 값이다.
 */
final class ProductPaymentDeadline {

    private ProductPaymentDeadline() {
    }

    /** 마감 시각. 서버 시간대의 오프셋이 붙는다. 마감이 없으면 {@code null}. */
    static OffsetDateTime at(LocalDateTime deadline, Clock clock) {
        return deadline == null ? null : deadline.atZone(clock.getZone()).toOffsetDateTime();
    }

    /** 지금부터 마감까지 남은 초. 이미 지났으면 0, 마감이 없으면 {@code null}. */
    static Long remainingSeconds(LocalDateTime deadline, Clock clock) {
        if (deadline == null) {
            return null;
        }
        long remaining = Duration.between(LocalDateTime.now(clock), deadline).toSeconds();
        return Math.max(remaining, 0L);
    }
}
