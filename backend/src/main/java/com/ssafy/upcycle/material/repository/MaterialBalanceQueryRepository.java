package com.ssafy.upcycle.material.repository;

import static com.ssafy.upcycle.material.entity.QMaterial.material;
import static com.ssafy.upcycle.material.entity.QMaterialBalance.materialBalance;
import static com.ssafy.upcycle.material.entity.QMaterialLock.materialLock;

import com.querydsl.core.types.Expression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.ssafy.upcycle.material.dto.MaterialBalanceResponse;
import com.ssafy.upcycle.material.dto.MyMaterialResponse;
import com.ssafy.upcycle.material.entity.MaterialLockStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 잔량 조회. <b>QueryDSL 로 짜는 것이 계약이다.</b>
 *
 * <p>{@code MaterialBalanceRepository} 주석이 이 클래스를 지목해 두었다 —
 * *"available 집계는 여기 넣지 않는다 … Balance 조회 티켓에서 QueryDSL 로 한 번에 집계한다"*.
 * JPA 파생 메서드로 우회하면 소재마다 잠금을 다시 읽어 N+1 이 된다.
 *
 * <h2>ACTIVE 잠금 합계를 스칼라 서브쿼리로 뺀다</h2>
 *
 * <p>{@code material_lock} 을 {@code LEFT JOIN} 해서 {@code SUM} 하는 방법도 있지만
 * 잠금이 2건 이상이면 <b>보유자 행이 잠금 수만큼 불어난다.</b> {@code groupBy} 로 다시
 * 접어야 하고, 그 과정에서 잠금이 없는 보유자를 잃기 쉽다.
 *
 * <p>그래서 상관 서브쿼리로 합계만 가져온다. 행이 불어나지 않고, 잠금이 없으면
 * {@code coalesce} 가 0 을 준다. 인덱스
 * {@code idx_material_lock_available (material_id, owner_id, status)} 가 이 조회 경로다.
 *
 * <h2>왜 ACTIVE 만 빼는가</h2>
 *
 * <p>{@code status} 는 {@code ACTIVE / RELEASED / CONSUMED / EXPIRED} 4종이다.
 * {@code RELEASED}·{@code CONSUMED} 는 이미 끝난 잠금이라 빼면 사용 가능 수량이
 * 영구히 줄어든다. {@code EXPIRED} 로 전환해 주는 배치가 아직 없어서
 * {@code expires_at} 이 지난 {@code ACTIVE} 도 그대로 빼는데, <b>과소 계산이 안전하다</b> —
 * 실제보다 적게 보여 판매를 못 하는 편이 초과 판매보다 낫다.
 */
@Repository
@RequiredArgsConstructor
public class MaterialBalanceQueryRepository {

    /**
     * 그 보유자의 {@code ACTIVE} 잠금 합계.
     *
     * <p>{@code materialBalance} 의 컬럼을 참조하는 <b>상관 서브쿼리</b>다.
     * {@code coalesce(0)} 가 없으면 잠금이 없을 때 {@code null} 이 와서
     * {@code available} 계산 전체가 {@code null} 로 번진다.
     *
     * <p><b>상수로 한 번만 만든다.</b> 매번 새로 만들면 {@code Tuple.get(expression)} 이
     * 다른 객체를 받아 값을 찾지 못하고 {@code null} 을 돌려준다.
     */
    private static final Expression<Long> ACTIVE_LOCKED_AMOUNT =
            JPAExpressions.select(materialLock.amount.sum().coalesce(0L))
                    .from(materialLock)
                    .where(
                            materialLock.materialId.eq(materialBalance.materialId),
                            materialLock.ownerId.eq(materialBalance.ownerId),
                            materialLock.status.eq(MaterialLockStatus.ACTIVE));

    private final JPAQueryFactory queryFactory;

    /** 한 소재의 보유자별 잔량. 보유 행이 없으면 빈 목록이다 (404 로 만들지 않는다). */
    public List<MaterialBalanceResponse> findBalancesByMaterialId(Long materialId) {
        return queryFactory
                .select(
                        materialBalance.ownerId,
                        materialBalance.walletAddress,
                        materialBalance.quantity,
                        ACTIVE_LOCKED_AMOUNT,
                        materialBalance.syncedAt)
                .from(materialBalance)
                .where(materialBalance.materialId.eq(materialId))
                // 많이 가진 보유자를 앞에 둔다. 동률이면 id 로 고정해 페이지가 흔들리지 않게 한다.
                .orderBy(materialBalance.quantity.desc(), materialBalance.ownerId.asc())
                .fetch()
                .stream()
                .map(row -> MaterialBalanceResponse.of(
                        row.get(materialBalance.ownerId),
                        row.get(materialBalance.walletAddress),
                        nullSafe(row.get(materialBalance.quantity)),
                        nullSafe(row.get(ACTIVE_LOCKED_AMOUNT)),
                        row.get(materialBalance.syncedAt)))
                .toList();
    }

    /**
     * 내가 보유한 소재 목록.
     *
     * <p>{@code material} 과 조인해 소재 메타데이터를 함께 가져온다. 목록에서 소재마다
     * 상세를 다시 부르면 그게 바로 N+1 이다. {@code material_balance} 의 UNIQUE
     * {@code (material_id, owner_id)} 덕에 소재당 내 행은 최대 1개라 행이 불어나지 않는다.
     */
    public List<MyMaterialResponse> findMyMaterials(Long ownerId) {
        return queryFactory
                .select(
                        material.id,
                        material.materialCode,
                        material.materialType,
                        material.rootOrigin,
                        material.generationType,
                        material.name,
                        material.grade,
                        material.status,
                        material.totalQuantity,
                        materialBalance.quantity,
                        ACTIVE_LOCKED_AMOUNT,
                        materialBalance.syncedAt)
                .from(materialBalance)
                .join(material)
                .on(material.id.eq(materialBalance.materialId))
                .where(materialBalance.ownerId.eq(ownerId))
                .orderBy(materialBalance.syncedAt.desc(), material.id.asc())
                .fetch()
                .stream()
                .map(row -> {
                    long quantity = nullSafe(row.get(materialBalance.quantity));
                    long locked = nullSafe(row.get(ACTIVE_LOCKED_AMOUNT));
                    return new MyMaterialResponse(
                            row.get(material.id),
                            row.get(material.materialCode),
                            row.get(material.materialType),
                            row.get(material.rootOrigin),
                            row.get(material.generationType),
                            row.get(material.name),
                            row.get(material.grade),
                            row.get(material.status),
                            nullSafe(row.get(material.totalQuantity)),
                            quantity,
                            locked,
                            quantity - locked,
                            row.get(materialBalance.syncedAt));
                })
                .toList();
    }

    /**
     * {@code null} 을 0 으로.
     *
     * <p>서브쿼리에 {@code coalesce} 를 걸어 두었지만 Tuple 에서 꺼낼 때는 박싱된
     * {@code Long} 이다. {@code long} 으로 바로 받으면 NPE 가 나는데, 그 스택트레이스는
     * 원인이 잠금 부재라는 것을 알려주지 않는다.
     */
    private static long nullSafe(Long value) {
        return value == null ? 0L : value;
    }
}
