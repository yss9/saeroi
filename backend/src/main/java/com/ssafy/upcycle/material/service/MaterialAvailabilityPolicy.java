package com.ssafy.upcycle.material.service;

import com.ssafy.upcycle.material.entity.MaterialLock;
import com.ssafy.upcycle.material.entity.MaterialLockStatus;
import java.time.LocalDateTime;
import java.util.Collection;

/**
 * <b>"이 소재를 지금 얼마나 더 쓸 수 있는가" 의 규칙. 이 프로젝트에서 한 곳뿐이어야 한다.</b>
 *
 * <pre>
 *   available = 보유량 − (기한이 남은 ACTIVE 잠금 합계)
 * </pre>
 *
 * <h2>규칙과 읽는 방식을 나눈다</h2>
 *
 * <p>같은 계산이 필요한 곳이 여럿인데 <b>DB 를 읽는 방식은 서로 다르다.</b> 판매 등록은
 * 잠금 없는 읽기로 충분하고, 생산 투입과 가공 요청은 동시 요청을 막아야 해서 보유·잠금
 * 행을 비관적으로 잠그고 읽는다. 그래서 읽기까지 한 메서드로 합칠 수 없다.
 *
 * <p>합칠 수 있는 것은 <b>규칙</b>이다 — 어떤 잠금을 세고 만료를 어떻게 다루는가.
 * 그 판정이 호출부마다 복사되면 잠금 정책이 바뀔 때 한쪽만 고치게 되고, 그러면
 * <b>화면은 팔 수 있다고 하는데 등록은 거절되는</b> 상태가 된다. 사용자에게는 설명할 수
 * 없는 오류로 보인다.
 *
 * <h2>어떤 잠금을 세는가 — 도메인 규칙 그대로</h2>
 *
 * <p>{@code .harness/reference/02-domain-rules.md} 의 Lock 상태 표가 정본이다.
 *
 * <pre>
 *   ACTIVE     잠금 중                                  → 뺀다
 *   RELEASED   취소·실패·반려로 해제. 수량 복귀          → 빼지 않는다
 *   CONSUMED   실제 소비 완료. 보유량 자체가 감소        → 빼지 않는다
 *   EXPIRED    기한 경과.
 *              **전환 전이라도 available 계산에서 제외**  → 빼지 않는다
 * </pre>
 *
 * <p><b>마지막 줄이 핵심이다.</b> 상태를 {@code EXPIRED} 로 바꿔 주는 배치가 아직 없어서,
 * 기한이 지난 잠금이 DB 에는 {@code ACTIVE} 로 남아 있다. 상태만 보면 이미 끝난 잠금이
 * 사용 가능 수량을 영영 깎는다.
 *
 * <p><b>목적({@code purpose})은 보지 않는다.</b> 판매용이든 가공용이든 생산용이든, 잠긴
 * 수량은 다른 곳에 쓸 수 없다는 점에서 같다. 목적별로 예외를 두면 같은 수량을 두 곳에
 * 걸 수 있게 된다.
 *
 * <h2>SQL 구현이 하나 더 있다</h2>
 *
 * <p>{@code MaterialBalanceQueryRepository#findMyMaterials} 는 목록 화면을 위해 같은 계산을
 * SQL 로 한다 — 소재마다 잠금을 다시 읽으면 N+1 이라 자바로 옮길 수 없다. 두 구현이 같은
 * 답을 하는지는 {@code MaterialAvailabilityConsistencyTests} 가 고정한다.
 */
public final class MaterialAvailabilityPolicy {

    private MaterialAvailabilityPolicy() {}

    /**
     * 사용 가능 수량.
     *
     * <p><b>음수를 그대로 돌려준다.</b> 잠금 합이 보유량을 넘었다는 뜻이고, 그것은 잔액
     * 동기화가 어긋났다는 신호다. 0 으로 접으면 그 신호가 사라진다 —
     * {@code GET /api/materials/me} 도 같은 값을 음수로 노출한다.
     *
     * @param balanceQuantity 보유량 (gram)
     * @param locks 이 소재·보유자의 잠금. {@code ACTIVE} 가 아닌 것이 섞여 있어도 된다
     * @param at 만료 판정 기준 시각
     */
    public static long available(
            long balanceQuantity, Collection<MaterialLock> locks, LocalDateTime at) {
        return balanceQuantity - lockedQuantity(locks, at);
    }

    /**
     * 지금 묶여 있는 수량.
     *
     * <p>호출부가 이미 {@code ACTIVE} 만 조회해 왔더라도 여기서 다시 거른다. 조회 조건이
     * 호출부마다 다른데 그 차이가 규칙에 스며들면, 한쪽만 {@code RELEASED} 를 세는 식으로
     * 조용히 갈라진다.
     */
    public static long lockedQuantity(Collection<MaterialLock> locks, LocalDateTime at) {
        if (locks == null || locks.isEmpty()) {
            return 0L;
        }
        return locks.stream()
                .filter(lock -> lock.getStatus() == MaterialLockStatus.ACTIVE)
                .filter(lock -> !lock.isExpiredAt(at))
                .mapToLong(MaterialLock::getAmount)
                .sum();
    }
}
