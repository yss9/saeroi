package com.ssafy.upcycle.trade.service;

import com.ssafy.upcycle.material.entity.MaterialLock;
import com.ssafy.upcycle.material.entity.MaterialLockStatus;
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
 * 가공·생산이 같은 계산을 필요로 하면 그때 {@code material} 쪽으로 옮긴다.
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
     */
    @Transactional(readOnly = true)
    public long availableQuantity(Long materialId, Long ownerId) {
        return balanceRepository
                .findByMaterialIdAndOwnerId(materialId, ownerId)
                .map(balance -> balance.getQuantity() - lockedQuantity(materialId, ownerId))
                .map(available -> Math.max(available, 0L))
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
     * <p>음수 가용량이 나오는 경우({@code Math.max} 로 접는 지점)는 데이터가 이미 어긋난
     * 것이다 — 잠금 합이 보유량을 넘었다는 뜻이다. 여기서 예외를 던지면 판매·구매가 통째로
     * 막히므로 0 으로 접고, 바로잡는 것은 동기화 쪽 책임으로 둔다.
     */
    private long lockedQuantity(Long materialId, Long ownerId) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<MaterialLock> locks = lockRepository.findAllByMaterialIdAndOwnerIdAndStatus(
                materialId, ownerId, MaterialLockStatus.ACTIVE);
        return locks.stream()
                .filter(lock -> !lock.isExpiredAt(now))
                .mapToLong(MaterialLock::getAmount)
                .sum();
    }
}
