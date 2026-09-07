package com.ssafy.upcycle.material.dto;

import java.time.LocalDateTime;

/**
 * 한 보유자의 잔량.
 *
 * <p><b>{@code available} 은 {@code quantity − ACTIVE 잠금 합계} 다.</b>
 * 잠금 상태 4종 중 {@code ACTIVE} 만 뺀다 — {@code RELEASED}·{@code CONSUMED} 까지 빼면
 * 이미 끝난 거래가 사용 가능 수량을 영구히 깎는다.
 *
 * <p><b>기한이 지난 잠금은 차감하지 않는다.</b> 도메인 규칙이
 * *"{@code EXPIRED} 는 배치가 전환하며, 전환 전이라도 available 계산에서 제외"* 로
 * 못 박고 있다 ({@code 02-domain-rules.md}). 상태를 전환해 주는 배치가 아직 없어
 * 만료된 잠금이 DB 에는 {@code ACTIVE} 로 남아 있으므로, 쿼리가 {@code expires_at} 을
 * 직접 본다. 그러지 않으면 이미 끝난 잠금이 사용 가능 수량을 계속 깎는다.
 *
 * <p>{@code available} 이 음수면 <b>0 으로 깎지 않고 그대로 내려준다.</b> 잠금 합계가
 * 보유량을 넘었다는 뜻이고 그건 데이터가 깨진 신호다. 숨기면 원인을 못 찾는다.
 *
 * <p>{@code syncedAt} 이 중요하다. 이 값은 온체인 잔액의 Projection 이고 그것을 채우는
 * 동기화가 아직 없다. 시각이 없으면 <b>"보유자가 없다" 와 "동기화가 한 번도 돌지 않았다"
 * 를 구분할 수 없고</b>, 둘 다 빈 목록으로 보인다.
 */
public record MaterialBalanceResponse(
        Long ownerId,
        String walletAddress,
        long quantity,
        long lockedQuantity,
        long available,
        LocalDateTime syncedAt) {

    public static MaterialBalanceResponse of(
            Long ownerId,
            String walletAddress,
            long quantity,
            long lockedQuantity,
            LocalDateTime syncedAt) {
        return new MaterialBalanceResponse(
                ownerId, walletAddress, quantity, lockedQuantity, quantity - lockedQuantity, syncedAt);
    }
}
