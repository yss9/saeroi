package com.ssafy.upcycle.material.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.MaterialErrorCode;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialLineageRelationType;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialLineageRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 혈통 기록의 도메인 규칙.
 *
 * <p>여기서 막는 것은 <b>되돌릴 수 없는 오염</b>이다. 혈통은 추적성의 근거라 잘못 기록되면
 * 지울 수 없고(거래 이력이 참조한다), 업사이클 비율 계산까지 함께 틀어진다.
 * Processing(4번)·Production(5번)이 각자 {@code MaterialLineage.of(...)} 를 직접 부르면
 * 아래 규칙이 도메인마다 따로 구현되고 그중 하나가 빠진다 — 그래서 이 서비스가 유일한 지점이다.
 */
@SpringBootTest
class MaterialLineageRegistrarTests {

    private static final long REGISTRANT = 9_201L;

    @Autowired
    private MaterialLineageRegistrar registrar;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private MaterialLineageRepository materialLineageRepository;

    @AfterEach
    void tearDown() {
        materialLineageRepository.deleteAll();
        materialRepository.deleteAll();
    }

    @Test
    void linksAChildToItsParent() {
        Material parent = material("MAT-200001", MaterialRootOrigin.WASTE);
        Material child = material("MAT-200002", MaterialRootOrigin.WASTE);

        registrar.link(child.getId(), parent.getId(), MaterialLineageRelationType.PROCESSING, 8_001L);

        assertThat(materialLineageRepository.findAllByChildMaterialId(child.getId()))
                .singleElement()
                .satisfies(edge -> {
                    assertThat(edge.getParentMaterialId()).isEqualTo(parent.getId());
                    assertThat(edge.getReferenceId()).isEqualTo(8_001L);
                });
    }

    @Test
    void rejectsASecondDifferentParent() {
        // 02-domain-rules §2: "혼합 가공을 지원하지 않으므로 부모는 항상 하나다."
        // 부모가 둘이면 root_origin 상속이 모호해지고 업사이클 비율을 한 값으로 판단할 수 없다.
        Material first = material("MAT-200001", MaterialRootOrigin.WASTE);
        Material second = material("MAT-200002", MaterialRootOrigin.WASTE);
        Material child = material("MAT-200003", MaterialRootOrigin.WASTE);
        registrar.link(child.getId(), first.getId(), MaterialLineageRelationType.PROCESSING, 8_001L);

        assertThatThrownBy(() -> registrar.link(
                        child.getId(), second.getId(), MaterialLineageRelationType.PROCESSING, 8_002L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MaterialErrorCode.MATERIAL_LINEAGE_MULTIPLE_PARENTS);
    }

    @Test
    void allowsTheSameParentAgainForADifferentJob() {
        // UNIQUE 키에 reference_id 가 포함된 이유. 자투리를 재투입하는 흐름이 이렇다.
        Material parent = material("MAT-200001", MaterialRootOrigin.WASTE);
        Material child = material("MAT-200002", MaterialRootOrigin.WASTE);
        registrar.link(child.getId(), parent.getId(), MaterialLineageRelationType.PROCESSING, 8_001L);

        registrar.link(child.getId(), parent.getId(), MaterialLineageRelationType.PROCESSING, 8_002L);

        assertThat(materialLineageRepository.findAllByChildMaterialId(child.getId())).hasSize(2);
    }

    @Test
    void rejectsTheSameParentForTheSameJob() {
        // DB UNIQUE 가 잡지만 그 예외는 사용자에게 설명할 수 없다.
        Material parent = material("MAT-200001", MaterialRootOrigin.WASTE);
        Material child = material("MAT-200002", MaterialRootOrigin.WASTE);
        registrar.link(child.getId(), parent.getId(), MaterialLineageRelationType.PROCESSING, 8_001L);

        assertThatThrownBy(() -> registrar.link(
                        child.getId(), parent.getId(), MaterialLineageRelationType.PROCESSING, 8_001L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MaterialErrorCode.MATERIAL_LINEAGE_DUPLICATE);
    }

    @Test
    void rejectsAChildWhoseRootOriginDiffersFromTheParent() {
        // 02-domain-rules §2: "root_origin — 출처. 자식 생성 시 부모에서 그대로 복사".
        // WASTE 계열에서 VIRGIN 자식이 나오면 업사이클 비율 판단이 깨진다.
        Material parent = material("MAT-200001", MaterialRootOrigin.WASTE);
        Material child = material("MAT-200002", MaterialRootOrigin.VIRGIN);

        assertThatThrownBy(() -> registrar.link(
                        child.getId(), parent.getId(), MaterialLineageRelationType.PROCESSING, 8_001L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MaterialErrorCode.MATERIAL_LINEAGE_ROOT_ORIGIN_MISMATCH);
    }

    @Test
    void rejectsAnUnknownMaterial() {
        Material parent = material("MAT-200001", MaterialRootOrigin.WASTE);

        assertThatThrownBy(() -> registrar.link(
                        999_999L, parent.getId(), MaterialLineageRelationType.PROCESSING, 8_001L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MaterialErrorCode.MATERIAL_NOT_FOUND);
    }

    @Test
    void rejectsLinkingAMaterialToItself() {
        // 엔티티가 막는 규칙이지만, 서비스를 거쳐도 그대로 막히는지 확인한다.
        // schema.sql 의 chk_material_lineage_no_self 와 이중으로 걸려 있다.
        Material material = material("MAT-200001", MaterialRootOrigin.WASTE);

        assertThatThrownBy(() -> registrar.link(
                        material.getId(),
                        material.getId(),
                        MaterialLineageRelationType.PROCESSING,
                        8_001L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Material material(String code, MaterialRootOrigin rootOrigin) {
        return materialRepository.save(Material.register(
                code,
                MaterialType.DENIM,
                rootOrigin,
                "혈통 규칙 테스트 " + code,
                null,
                "A",
                1_000L,
                null,
                null,
                REGISTRANT));
    }
}
