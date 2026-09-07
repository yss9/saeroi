package com.ssafy.upcycle.material.dto;

import java.time.LocalDateTime;

/**
 * 한 보유자의 잔량.
 *
 * <p><b>{@code available} 은 {@code quantity − ACTIVE 잠금 합계} 다.</b>
 * 잠금 상태 4종 중 {@code ACTIVE} 만 뺀다 — {@code RELEASED}·{@code CONSUMED} 까지 빼면
 * 이미 끝난 거래가 사용 가능 수량을 영구히 깎는다.
 *
 * <p>{@code EXPIRED} 로 바꿔주는 주체가 아직 없어서, {@code expires_at} 이 지난
 * {@code ACTIVE} 잠금도 그대로 뺀다. <b>과소 계산하는 쪽이 안전하다</b> —
 * 실제보다 적게 보여 판매를 못 하는 것이 초과 판매보다 낫다.
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
