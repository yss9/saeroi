package com.ssafy.upcycle.material.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.material.dto.MaterialLineageResponse;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialLineage;
import com.ssafy.upcycle.material.entity.MaterialLineageRelationType;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialLineageRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 혈통 역추적. 스토리 {@code -23} 의 Acceptance Criteria 세 가지를 고정한다 —
 * 부모 조회 · 자식 조회 · <b>최초 Source 까지 역추적</b>.
 *
 * <p>여기서 특히 신경 쓴 것은 <b>상한 표기</b>다. 깊이 5 · 노드 200 을 넘으면
 * 규칙이 *"이전 이력은 일부 생략되었습니다"* 로 표기하라고 정했다. 잘렸는데 표기가
 * 없으면 사용자는 그게 전체 이력이라고 믿는다 — 추적성 기능에서 가장 나쁜 거짓이다.
 *
 * <p>{@code @SpringBootTest} 는 롤백이 없어 커밋한 행이 같은 H2 에 남는다.
 * {@code @AfterEach} 로 지우고, 전역 조회 대신 이 테스트가 만든 소재만 단언한다.
 */
@SpringBootTest
class MaterialLineageQueryServiceTests {

    private static final long REGISTRANT = 9_101L;

    @Autowired
    private MaterialLineageQueryService lineageQueryService;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private MaterialLineageRepository materialLineageRepository;

    @AfterEach
    void tearDown() {
        materialLineageRepository.deleteAll();
        materialRepository.deleteAll();
    }

    // ── 스토리 -23 Acceptance Criteria ────────────────────────

    @Test
    void findsTheParentMaterial() {
        Material parent = material("MAT-100001");
        Material child = material("MAT-100002");
        link(child, parent, 7_001L);

        MaterialLineageResponse lineage = lineageQueryService.findLineage(child.getId());

        assertThat(lineage.nodes()).extracting(MaterialLineageResponse.Node::materialCode)
                .containsExactlyInAnyOrder("MAT-100002", "MAT-100001");
        assertThat(depthOf(lineage, "MAT-100001"))
                .as("조상은 음수 깊이여야 방향이 드러난다")
                .isEqualTo(-1);
    }

    @Test
    void findsChildMaterialsCreatedByProcessing() {
        Material parent = material("MAT-100001");
        Material first = material("MAT-100002");
        Material second = material("MAT-100003");
        link(first, parent, 7_001L);
        link(second, parent, 7_002L);

        MaterialLineageResponse lineage = lineageQueryService.findLineage(parent.getId());

        assertThat(lineage.nodes()).extracting(MaterialLineageResponse.Node::materialCode)
                .containsExactlyInAnyOrder("MAT-100001", "MAT-100002", "MAT-100003");
        assertThat(depthOf(lineage, "MAT-100002")).isEqualTo(1);
        assertThat(depthOf(lineage, "MAT-100003")).isEqualTo(1);
    }

    @Test
    void tracesBackToTheOriginalSource() {
        // AC: "최초 Source까지 역추적할 수 있다". 3세대를 만들어 끝까지 올라가는지 본다.
        List<Material> chain = chain(4);

        MaterialLineageResponse lineage = lineageQueryService.findLineage(chain.get(3).getId());

        assertThat(lineage.nodes()).hasSize(4);
        assertThat(depthOf(lineage, chain.get(0).getMaterialCode()))
                .as("최초 소재까지 3세대 거슬러 올라간다")
                .isEqualTo(-3);
        assertThat(lineage.truncated()).isFalse();
    }

    @Test
    void seesBothDirectionsFromTheMiddle() {
        // 가운데 소재를 조회하면 위와 아래가 함께 나와야 한다.
        List<Material> chain = chain(3);

        MaterialLineageResponse lineage = lineageQueryService.findLineage(chain.get(1).getId());

        assertThat(depthOf(lineage, chain.get(0).getMaterialCode())).isEqualTo(-1);
        assertThat(depthOf(lineage, chain.get(1).getMaterialCode())).isZero();
        assertThat(depthOf(lineage, chain.get(2).getMaterialCode())).isEqualTo(1);
    }

    // ── 상한 표기 ────────────────────────────────────────────

    @Test
    void doesNotClaimTruncationWhenTheChainEndsExactlyAtTheDepthLimit() {
        // 깊이 5 에 정확히 끝나는 혈통을 "일부 생략" 으로 표시하면, 사용자는 있지도 않은
        // 이력을 찾는다. 마지막 세대에 노드가 있다는 것만으로 잘렸다고 보면 안 된다.
        List<Material> chain = chain(MaterialLineageQueryService.MAX_DEPTH + 1);

        MaterialLineageResponse lineage =
                lineageQueryService.findLineage(chain.get(chain.size() - 1).getId());

        assertThat(lineage.nodes()).hasSize(MaterialLineageQueryService.MAX_DEPTH + 1);
        assertThat(lineage.truncated()).isFalse();
        assertThat(lineage.message()).isNull();
    }

    @Test
    void marksTruncatedWhenTheChainIsDeeperThanTheLimit() {
        List<Material> chain = chain(MaterialLineageQueryService.MAX_DEPTH + 3);

        MaterialLineageResponse lineage =
                lineageQueryService.findLineage(chain.get(chain.size() - 1).getId());

        assertThat(lineage.truncated()).isTrue();
        assertThat(lineage.message())
                .as("규칙이 정한 문구를 그대로 내려줘야 화면이 표시할 수 있다")
                .isEqualTo(MaterialLineageQueryService.TRUNCATED_MESSAGE);
    }

    @Test
    void stopsAtTheDepthLimit() {
        List<Material> chain = chain(MaterialLineageQueryService.MAX_DEPTH + 3);

        MaterialLineageResponse lineage =
                lineageQueryService.findLineage(chain.get(chain.size() - 1).getId());

        assertThat(lineage.nodes())
                .as("조회 대상 + 깊이 상한만큼")
                .hasSize(MaterialLineageQueryService.MAX_DEPTH + 1);
        assertThat(lineage.nodes()).extracting(MaterialLineageResponse.Node::depth)
                .allMatch(depth -> depth >= -MaterialLineageQueryService.MAX_DEPTH);
    }

    // ── 그래프 형태 ──────────────────────────────────────────

    @Test
    void returnsEachMaterialOnlyOnce() {
        // 자투리 재투입처럼 같은 소재가 두 경로에 나타나도 노드는 한 번만 담긴다.
        // 중첩 트리로 내려주면 복제되어 화면이 같은 것을 다르게 보여준다.
        Material root = material("MAT-100001");
        Material mid = material("MAT-100002");
        link(mid, root, 7_001L);
        link(mid, root, 7_002L); // 같은 쌍, 다른 작업 — UNIQUE 키에 reference 가 있어 허용된다

        MaterialLineageResponse lineage = lineageQueryService.findLineage(mid.getId());

        assertThat(lineage.nodes()).hasSize(2);
        assertThat(lineage.edges())
                .as("간선은 작업별로 둘 다 남는다")
                .hasSize(2);
    }

    @Test
    void carriesRelationTypeAndReferenceOnEdges() {
        // 화면이 "↓ Processing" 을 그리려면 간선에 종류가 있어야 한다.
        Material parent = material("MAT-100001");
        Material child = material("MAT-100002");
        link(child, parent, 7_001L);

        MaterialLineageResponse lineage = lineageQueryService.findLineage(child.getId());

        assertThat(lineage.edges()).singleElement().satisfies(edge -> {
            assertThat(edge.parentMaterialId()).isEqualTo(parent.getId());
            assertThat(edge.childMaterialId()).isEqualTo(child.getId());
            assertThat(edge.relationType()).isEqualTo(MaterialLineageRelationType.PROCESSING);
            assertThat(edge.referenceId()).isEqualTo(7_001L);
        });
    }

    @Test
    void returnsOnlyTheTargetWhenThereIsNoLineage() {
        Material lonely = material("MAT-100001");

        MaterialLineageResponse lineage = lineageQueryService.findLineage(lonely.getId());

        assertThat(lineage.nodes()).singleElement()
                .extracting(MaterialLineageResponse.Node::depth)
                .isEqualTo(0);
        assertThat(lineage.edges()).isEmpty();
        assertThat(lineage.truncated()).isFalse();
    }

    // ── helpers ─────────────────────────────────────────────

    /** 부모 → 자식 사슬을 만든다. 반환 순서는 최초 소재부터다. */
    private List<Material> chain(int length) {
        List<Material> materials = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            materials.add(material(String.format("MAT-10%04d", i)));
        }
        for (int i = 1; i < length; i++) {
            link(materials.get(i), materials.get(i - 1), 7_000L + i);
        }
        return materials;
    }

    private Material material(String code) {
        return materialRepository.save(Material.register(
                code,
                MaterialType.DENIM,
                MaterialRootOrigin.WASTE,
                "혈통 테스트 소재 " + code,
                null,
                "A",
                1_000L,
                null,
                null,
                REGISTRANT));
    }

    private void link(Material child, Material parent, long referenceId) {
        materialLineageRepository.save(MaterialLineage.of(
                child.getId(),
                parent.getId(),
                MaterialLineageRelationType.PROCESSING,
                referenceId));
    }

    private static int depthOf(MaterialLineageResponse lineage, String materialCode) {
        return lineage.nodes().stream()
                .filter(node -> node.materialCode().equals(materialCode))
                .findFirst()
                .orElseThrow(() -> new AssertionError(materialCode + " 노드가 없다"))
                .depth();
    }
}
