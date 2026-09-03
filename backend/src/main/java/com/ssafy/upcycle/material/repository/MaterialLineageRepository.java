package com.ssafy.upcycle.material.repository;

import com.ssafy.upcycle.material.entity.MaterialLineage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 혈통은 양방향으로 탐색한다.
 *
 * <ul>
 *   <li>child 기준 — QR Journey 가 제품에서 최초 공급자까지 거슬러 올라가는 경로</li>
 *   <li>parent 기준 — 어떤 소재가 무엇으로 갈라졌는지 내려가는 경로</li>
 * </ul>
 *
 * <p>재귀 조회는 두지 않는다. 여러 세대를 거슬러 올라가는 순회는 호출부가 단계별로 돌거나,
 * 필요해지면 재귀 CTE 를 쓰는 편이 낫다. 엔티티 그래프로 풀면 깊이를 제어할 수 없다.
 */
public interface MaterialLineageRepository extends JpaRepository<MaterialLineage, Long> {

    List<MaterialLineage> findAllByChildMaterialId(Long childMaterialId);

    List<MaterialLineage> findAllByParentMaterialId(Long parentMaterialId);
}
