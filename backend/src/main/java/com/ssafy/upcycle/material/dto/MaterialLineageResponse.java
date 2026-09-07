package com.ssafy.upcycle.material.dto;

import com.ssafy.upcycle.material.entity.MaterialGenerationType;
import com.ssafy.upcycle.material.entity.MaterialLineageRelationType;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialStatus;
import com.ssafy.upcycle.material.entity.MaterialType;
import java.util.List;

/**
 * 소재 혈통. {@code GET /api/materials/{materialId}/lineage} 의 응답.
 *
 * <h2>왜 트리가 아니라 노드 + 간선인가</h2>
 *
 * <p>혈통은 트리가 아니다. {@code material_lineage} 는 간선을 행으로 두어 1:N(해체)과
 * N:1(혼합)을 모두 표현할 수 있고, 자투리 재투입까지 들어가면 같은 소재가 두 경로에
 * 나타난다. 중첩 트리로 내려주면 그 소재가 두 번 복제돼 화면이 같은 것을 다르게 보여준다.
 *
 * <p>노드를 한 번씩만 담고 간선을 따로 주면 클라이언트가 필요한 모양(체인·트리·그래프)으로
 * 조립한다. {@code -204} 의 화면은 이걸로 {@code MAT-001 ↓ Processing MAT-004} 를 그린다.
 *
 * <h2>{@code truncated} 를 반드시 읽을 것</h2>
 *
 * <p>{@code 02-domain-rules.md} 가 재귀 상한을 <b>깊이 5 · 노드 200</b> 으로 정하고,
 * *"초과 시 더 올라가지 않고 `이전 이력은 일부 생략되었습니다` 로 표기"* 라고 못 박았다.
 *
 * <p>그래서 이 응답은 <b>완전한 혈통이 아닐 수 있다.</b> {@code truncated} 가 {@code true} 면
 * 화면은 반드시 그 사실을 보여줘야 한다 — 안 보여주면 사용자는 잘린 이력을 전체로 오해하고,
 * 그건 추적성 기능에서 가장 나쁜 종류의 거짓이다. {@code message} 에 그 문구가 담겨 온다.
 *
 * <p>{@code depth} 는 조회 대상 소재를 0 으로 두고, 조상은 음수·자손은 양수다.
 * 부호로 방향이 드러나므로 목록을 두 개로 나누지 않았다.
 */
public record MaterialLineageResponse(
        Long materialId,
        String materialCode,
        List<Node> nodes,
        List<Edge> edges,
        /** 상한에 걸려 일부만 담겼는가 */
        boolean truncated,
        /** {@code truncated} 일 때만 채워진다. 화면에 그대로 보여줄 문구 */
        String message) {

    /** 혈통에 등장하는 소재 하나. 같은 소재는 한 번만 담긴다. */
    public record Node(
            Long materialId,
            String materialCode,
            MaterialType materialType,
            MaterialRootOrigin rootOrigin,
            MaterialGenerationType generationType,
            String name,
            MaterialStatus status,
            /** 조회 대상은 0, 조상은 음수, 자손은 양수 */
            int depth) {
    }

    /** 부모 → 자식 간선 하나. */
    public record Edge(
            Long parentMaterialId,
            Long childMaterialId,
            MaterialLineageRelationType relationType,
            /** PROCESSING 이면 processing_job PK, PRODUCTION_SCRAP 이면 production_batch PK */
            Long referenceId) {
    }
}
