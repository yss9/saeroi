package com.ssafy.upcycle.material.repository;

import com.ssafy.upcycle.material.entity.MaterialComposition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialCompositionRepository
        extends JpaRepository<MaterialComposition, Long> {

    /**
     * 소재의 구성. 순서를 <b>비율 내림차순</b>으로 고정한다.
     *
     * <p>정렬을 지정하지 않으면 DB 가 편한 순서로 돌려주고, 화면에서 같은 소재를 두 번
     * 열었을 때 줄 순서가 달라 보인다. 주 성분이 위에 오는 것이 읽는 순서이기도 하다.
     * 비율이 같으면 이름으로 갈라 완전히 결정적으로 만든다.
     */
    List<MaterialComposition> findByMaterialIdOrderByPercentageDescFiberTypeAsc(Long materialId);
}
