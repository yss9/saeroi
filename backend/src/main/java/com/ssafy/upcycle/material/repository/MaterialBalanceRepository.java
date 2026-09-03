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
}
