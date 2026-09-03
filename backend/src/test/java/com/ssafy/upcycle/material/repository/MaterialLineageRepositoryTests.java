package com.ssafy.upcycle.material.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.common.jpa.config.JpaAuditingConfig;
import com.ssafy.upcycle.material.entity.MaterialLineage;
import com.ssafy.upcycle.material.entity.MaterialLineageRelationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class MaterialLineageRepositoryTests {

    @Autowired
    private MaterialLineageRepository materialLineageRepository;

    @Autowired
    private TestEntityManager entityManager;

    /** 해체 가공: 폐청바지 하나가 데님·안감·금속 셋으로 갈라진다. */
    @Test
    void oneParentCanHaveManyChildren() {
        saveEdge(2L, 1L, 100L);
        saveEdge(3L, 1L, 100L);
        saveEdge(4L, 1L, 100L);
        entityManager.flush();
        entityManager.clear();

        assertThat(materialLineageRepository.findAllByParentMaterialId(1L))
                .hasSize(3)
                .extracting(MaterialLineage::getChildMaterialId)
                .containsExactlyInAnyOrder(2L, 3L, 4L);
    }

    /** 혼합 재생: 데님 두 종이 재생원단 하나로 합쳐진다. 컬럼 하나로는 표현할 수 없는 방향. */
    @Test
    void oneChildCanHaveManyParents() {
        saveEdge(20L, 1L, 200L);
        saveEdge(20L, 10L, 200L);
        entityManager.flush();
        entityManager.clear();

        assertThat(materialLineageRepository.findAllByChildMaterialId(20L))
                .hasSize(2)
                .extracting(MaterialLineage::getParentMaterialId)
                .containsExactlyInAnyOrder(1L, 10L);
    }

    @Test
    void persistedEdgeGetsCreatedAt() {
        MaterialLineage saved = saveEdge(2L, 1L, 100L);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    private MaterialLineage saveEdge(Long child, Long parent, Long referenceId) {
        return materialLineageRepository.save(MaterialLineage.of(
                child, parent, MaterialLineageRelationType.PROCESSING, referenceId));
    }
}
