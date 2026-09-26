package com.ssafy.upcycle.trade.service;

import com.ssafy.upcycle.material.entity.MaterialLock;
import com.ssafy.upcycle.material.entity.MaterialLockStatus;
import com.ssafy.upcycle.material.service.MaterialAvailabilityPolicy;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * "이 사람이 지금 이 소재를 얼마나 더 쓸 수 있는가" 한 가지만 답한다.
 *
 * <pre>
 *   available = balance.quantity - (만료되지 않은 ACTIVE 잠금 합계)
 * </pre>
 *
 * <p><b>보유량과 가용량은 다르다.</b> 판매 등록·가공 요청은 수량을 잠그되 보유량은 그대로
 * 둔다 (소유권이 아직 안 넘어갔으므로). 그래서 보유량만 보고 판매를 허용하면 <b>같은 30kg 을
 * 여러 판매 글에 중복으로 걸 수 있다.</b> 이 계산이 그것을 막는 유일한 지점이다.
 *
 * <p>만료된 잠금을 빼는 이유는 {@code expires_at} 이 지난 잠금이 여전히 {@code ACTIVE} 로
 * 남아 있을 수 있어서다 — 상태를 {@code EXPIRED} 로 바꾸는 배치가 아직 없다. 그 잠금을
 * 세면 사용자는 이미 풀린 수량을 영영 쓰지 못한다.
 *
 * <p><b>목록 조회에서 쓰면 안 된다.</b> 소재마다 잠금을 다시 읽어 정확히 N+1 이 된다
 * ({@code MaterialBalanceRepository} 의 경고와 같은 이야기). 여기 쓰임은 판매 등록 한 건의
 * 검증이라 단건 조회 두 번이고, 목록용 집계는 Balance 조회 티켓의 몫이다.
 *
 * <p>{@code trade} 패키지에 둔 것은 지금 유일한 사용처가 판매 등록이기 때문이다.
 *
 * <p>⚠️ <b>같은 계산이 {@code MaterialBalanceQueryRepository} 에도 있다.</b> 그쪽은
 * {@code GET /api/materials/me} 가 화면에 보여줄 값을 SQL 로 집계하고, 여기는 판매 등록
 * 검증이 쓴다. 두 구현은 현재 <b>같은 값을 답하며</b>
 * {@code MaterialAvailabilityConsistencyTests} 가 그것을 고정한다.
 *
 * <p>구현이 둘인 것은 그 자체로 위험이다 — 잠금 정책이 바뀔 때 한쪽만 고치면 화면은
 * 팔 수 있다고 하는데 등록은 거절되는 상태가 된다. 한 곳으로 모으는 것은 {@code material}
 * 도메인 파일을 건드리는 일이라 담당자 협의가 필요하고, 그때까지는 위 테스트가 안전망이다.
 */
@Component
@RequiredArgsConstructor
public class MaterialAvailability {

    private final MaterialBalanceRepository balanceRepository;
    private final MaterialLockRepository lockRepository;
    private final Clock clock;

    /**
     * 가용 수량 (gram). 보유 행이 아예 없으면 0 이다 — "보유하지 않음" 과 "0 을 보유함" 을
     * 구분해야 하는 호출부는 {@link #owns} 를 함께 본다.
     *
     * <p><b>음수를 그대로 돌려준다.</b> 잠금 합이 보유량을 넘었다는 뜻이고, 그것은 온체인
     * 잔액 동기화가 어긋났다는 신호다. 0 으로 접으면 그 신호가 사라진다 —
     * {@code GET /api/materials/me} 는 같은 값을 음수로 노출하며
     * ({@code MaterialBalanceQueryRepository#findMyMaterials}, {@code InventoryItem} 주석의
     * *"음수 가능 — 데이터 파손 신호다"*) 두 경로가 같은 답을 해야 한다.
     *
     * <p>음수가 판매 등록을 망가뜨리지 않는다. 호출부의 비교는
     * {@code availableQuantity(...) < quantity} 이고 요청 수량은 항상 양수이므로,
     * 음수든 0 이든 거절이라는 결과는 같다. 달라지는 것은 <b>진단 정보가 남는지</b>뿐이다.
     */
    @Transactional(readOnly = true)
    public long availableQuantity(Long materialId, Long ownerId) {
        return balanceRepository
                .findByMaterialIdAndOwnerId(materialId, ownerId)
                .map(balance -> balance.getQuantity() - lockedQuantity(materialId, ownerId))
                .orElse(0L);
    }

    /** 보유 행이 있는가. 수량 0 이어도 참이다. */
    @Transactional(readOnly = true)
    public boolean owns(Long materialId, Long ownerId) {
        return balanceRepository.findByMaterialIdAndOwnerId(materialId, ownerId).isPresent();
    }

    /**
     * 만료되지 않은 ACTIVE 잠금의 합.
     *
     * <p><b>판정은 {@link MaterialAvailabilityPolicy} 가 한다.</b> 어떤 잠금을 세고 만료를
     * 어떻게 다루는지는 도메인 규칙이고, 그 규칙이 호출부마다 복사되면 잠금 정책이 바뀔 때
     * 한쪽만 고치게 된다. 여기 남는 것은 <b>읽는 방식</b>뿐이다 — 판매 등록은 잠금 없는
     * 읽기로 충분하고, 생산 투입은 같은 규칙을 비관적 잠금 읽기 위에 얹는다.
     */
    private long lockedQuantity(Long materialId, Long ownerId) {
        List<MaterialLock> locks = lockRepository.findAllByMaterialIdAndOwnerIdAndStatus(
                materialId, ownerId, MaterialLockStatus.ACTIVE);
        return MaterialAvailabilityPolicy.lockedQuantity(locks, LocalDateTime.now(clock));
    }
}
