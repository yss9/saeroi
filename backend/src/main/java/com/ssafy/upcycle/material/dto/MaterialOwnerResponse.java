package com.ssafy.upcycle.material.dto;

import com.ssafy.upcycle.material.entity.MaterialBalance;
import java.time.LocalDateTime;

/**
 * 소재를 현재 들고 있는 사람 한 명과 그 수량.
 *
 * <p><b>등록자가 아니다.</b> {@code material.registrantId} 는 누가 올렸는지이고, 거래가
 * 일어나면 보유자는 바뀐다. 화면이 "소유자" 를 물으면 이쪽이다.
 *
 * <p>{@code quantity} 는 {@code material_balance} 에 저장된 값 그대로다 —
 * <b>ACTIVE 잠금을 빼지 않았다.</b> 사용 가능 수량({@code available})은 별도 티켓 소관이고,
 * 두 곳에서 같은 계산을 하면 값이 갈라진다.
 *
 * <p>{@code syncedAt} 을 빼놓지 않은 이유가 중요하다. 이 테이블은 온체인 잔액의 Projection 이고
 * 그걸 채우는 동기화가 아직 없다. 시각이 없으면 <b>"보유자가 없다" 와 "동기화가 한 번도
 * 돌지 않았다" 를 구분할 수 없고</b>, 둘 다 빈 목록으로 보인다.
 */
public record MaterialOwnerResponse(
        Long ownerId, String walletAddress, long quantity, LocalDateTime syncedAt) {

    public static MaterialOwnerResponse from(MaterialBalance balance) {
        return new MaterialOwnerResponse(
                balance.getOwnerId(),
                balance.getWalletAddress(),
                balance.getQuantity(),
                balance.getSyncedAt());
    }
}
