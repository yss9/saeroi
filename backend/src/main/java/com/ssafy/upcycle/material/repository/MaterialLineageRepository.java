package com.ssafy.upcycle.material.repository;

import com.ssafy.upcycle.material.entity.MaterialLineage;
import java.util.Collection;
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

    /**
     * 여러 자식의 부모 간선을 한 번에 읽는다. 소재 목록의 가공 이력처럼 <b>여러 소재의 혈통을
     * 동시에</b> 거슬러 올라갈 때 쓴다 — 소재마다 위 메서드를 부르면 세대마다 N+1 이 된다.
     *
     * <p>세대별로 한 번씩 부르는 것이 전제다. 재귀를 두지 않는다는 이 인터페이스의 방침은
     * 그대로다.
     */
    List<MaterialLineage> findAllByChildMaterialIdIn(Collection<Long> childMaterialIds);

    List<MaterialLineage> findAllByParentMaterialId(Long parentMaterialId);
}
