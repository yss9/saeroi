package com.ssafy.upcycle.material.repository;

import com.ssafy.upcycle.material.entity.MaterialBalance;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 조회 메서드는 ERD 가 선언한 인덱스가 가리키는 접근 경로만 둔다 —
 * UNIQUE {@code (material_id, owner_id)} 와 INDEX {@code owner_id} 다.
 *
 * <p>available 집계는 여기 넣지 않는다. quantity 에서 ACTIVE 잠금 합계를 빼는 계산이라
 * material_lock 과의 조인이 필요하고, 목록 조회에서 소재마다 잠금을 다시 읽으면 N+1 이 된다.
 * Balance 조회 티켓에서 QueryDSL 로 한 번에 집계한다.
 */
public interface MaterialBalanceRepository extends JpaRepository<MaterialBalance, Long> {

    Optional<MaterialBalance> findByMaterialIdAndOwnerId(Long materialId, Long ownerId);

    List<MaterialBalance> findAllByOwnerId(Long ownerId);

    /**
     * 한 소재의 현재 보유자 전부.
     *
     * <p>UNIQUE {@code (material_id, owner_id)} 의 선행 컬럼만 쓰므로 그 인덱스를 그대로 탄다.
     *
     * <p>위 주석이 경고하는 N+1 은 <b>목록 조회</b> 이야기다. 소재 상세는 단건이라 이 조회가
     * 한 번만 나가므로 여기서는 해당하지 않는다. 목록에서 소재마다 이걸 부르면 그때가 바로
     * 그 N+1 이고, 그건 Balance 조회 티켓의 QueryDSL 집계로 풀어야 한다.
     *
     * <p><b>등록자와 보유자는 다르다.</b> {@code material.registrant_id} 는 누가 올렸는지이고
     * 거래가 일어나면 보유자는 바뀐다. 소유를 물을 때는 항상 이쪽을 본다.
     */
    List<MaterialBalance> findAllByMaterialId(Long materialId);
}
