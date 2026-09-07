package com.ssafy.upcycle.material.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.MaterialErrorCode;
import com.ssafy.upcycle.material.dto.MaterialLineageResponse;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialLineage;
import com.ssafy.upcycle.material.repository.MaterialLineageRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소재 혈통 조회.
 *
 * <h2>왜 재귀 CTE 가 아니라 단계별 BFS 인가</h2>
 *
 * <p>{@code MaterialLineageRepository} 주석이 방향을 정해 뒀다 —
 * *"재귀 조회는 두지 않는다. 여러 세대를 거슬러 올라가는 순회는 호출부가 단계별로 돌거나,
 * 필요해지면 재귀 CTE 를 쓰는 편이 낫다. 엔티티 그래프로 풀면 깊이를 제어할 수 없다."*
 *
 * <p>단계별 BFS 를 택한 이유는 <b>상한을 정확히 지킬 수 있어서</b>다.
 * 재귀 CTE 로도 깊이는 막을 수 있지만, 노드 200 상한은 "몇 개에서 멈췄는지" 를 알아야 하고
 * 그걸 SQL 안에서 처리하면 잘렸다는 사실이 응답까지 올라오지 않는다.
 * BFS 는 세대 경계에서 멈추므로 <b>잘렸다는 사실과 그 이유를 그대로 응답에 담을 수 있다.</b>
 *
 * <p>비용은 세대당 쿼리 1회다. 깊이 상한이 5 라 최악이 양방향 10회이고,
 * 각 쿼리가 인덱스({@code idx_material_lineage_child} / {@code _parent})를 탄다.
 *
 * <h2>상한 — 깊이 5 · 노드 200</h2>
 *
 * <p>{@code 02-domain-rules.md} 근거: 깊이는 회수를 제외해 순환이 없고 현실적으로 2~3세대라 5,
 * 노드는 *"성능 변수는 깊이가 아니라 분기 수 — 1:N 해체가 반복되면 깊이 3에서도 수십 개"* 라 200.
 *
 * <p><b>상한에 걸리면 조용히 자르지 않는다.</b> 규칙이 정한 문구를 응답에 담아
 * 화면이 "일부 생략" 을 표시할 수 있게 한다. 이게 없으면 잘린 이력이 전체로 보인다.
 */
@Service
@RequiredArgsConstructor
public class MaterialLineageQueryService {

    /** 조회 대상 기준 위·아래 각각의 최대 세대 수. */
    static final int MAX_DEPTH = 5;

    /** 조회 대상을 포함한 노드 총 상한. */
    static final int MAX_NODES = 200;

    static final String TRUNCATED_MESSAGE = "이전 이력은 일부 생략되었습니다";

    private final MaterialRepository materialRepository;
    private final MaterialLineageRepository materialLineageRepository;

    /**
     * 한 소재의 혈통을 양방향으로 조회한다.
     *
     * <p>위로는 부모(조상), 아래로는 자식(자손)을 따라간다. 스토리 {@code -23} 의
     * Acceptance Criteria 가 세 가지를 요구한다 — 부모 조회, 자식 조회, <b>최초 Source 까지 역추적</b>.
     * 그래서 한 번의 호출로 양쪽을 다 준다. 두 엔드포인트로 나누면 화면이 두 번 부르고
     * 그 사이 상한 계산이 서로 어긋난다.
     */
    @Transactional(readOnly = true)
    public MaterialLineageResponse findLineage(Long materialId) {
        Material target = materialRepository
                .findById(materialId)
                .orElseThrow(() -> new BusinessException(MaterialErrorCode.MATERIAL_NOT_FOUND));

        // 노드는 등장 순서를 유지한다. 화면이 정렬을 다시 하지 않아도 조회 대상이 앞에 온다.
        Map<Long, Integer> depthById = new LinkedHashMap<>();
        depthById.put(materialId, 0);

        Map<EdgeKey, MaterialLineage> edges = new LinkedHashMap<>();
        Traversal up = walk(materialId, depthById, edges, Direction.ANCESTORS);
        Traversal down = walk(materialId, depthById, edges, Direction.DESCENDANTS);

        boolean truncated = up.truncated() || down.truncated();
        return new MaterialLineageResponse(
                materialId,
                target.getMaterialCode(),
                toNodes(depthById),
                toEdges(edges.values()),
                truncated,
                truncated ? TRUNCATED_MESSAGE : null);
    }

    /** 탐색 방향. 간선을 어느 쪽으로 읽고 다음 세대를 어디서 꺼내는지가 다르다. */
    private enum Direction {
        ANCESTORS,
        DESCENDANTS;

        int sign() {
            return this == ANCESTORS ? -1 : 1;
        }
    }

    private record Traversal(boolean truncated) {
    }

    /** 간선 중복 판정 키. 같은 부모-자식이 다른 작업으로 두 번 이어질 수 있어 reference 를 포함한다. */
    private record EdgeKey(Long parentId, Long childId, Long referenceId) {
    }

    /**
     * 한 방향으로 세대별로 내려간다(또는 올라간다).
     *
     * <p>이미 본 소재는 다시 큐에 넣지 않는다 — 자투리 재투입처럼 같은 소재가 두 경로에
     * 나타나면 무한히 돌 수 있다. 회수를 제외해 순환이 없다는 것이 규칙의 전제지만,
     * 데이터가 그 전제를 깨더라도 여기서 멈춘다.
     */
    private Traversal walk(
            Long startId,
            Map<Long, Integer> depthById,
            Map<EdgeKey, MaterialLineage> edges,
            Direction direction) {
        Deque<Long> frontier = new ArrayDeque<>();
        frontier.add(startId);

        for (int depth = 1; depth <= MAX_DEPTH; depth++) {
            if (frontier.isEmpty()) {
                return new Traversal(false);
            }
            Deque<Long> next = new ArrayDeque<>();
            while (!frontier.isEmpty()) {
                Long current = frontier.poll();
                for (MaterialLineage edge : read(current, direction)) {
                    edges.putIfAbsent(
                            new EdgeKey(
                                    edge.getParentMaterialId(),
                                    edge.getChildMaterialId(),
                                    edge.getReferenceId()),
                            edge);
                    Long neighbour = direction == Direction.ANCESTORS
                            ? edge.getParentMaterialId()
                            : edge.getChildMaterialId();
                    if (depthById.containsKey(neighbour)) {
                        continue;
                    }
                    if (depthById.size() >= MAX_NODES) {
                        // 노드 상한. 이 간선까지는 담았으므로 화면은 "여기서 끊겼다" 를 알 수 있다.
                        return new Traversal(true);
                    }
                    depthById.put(neighbour, depth * direction.sign());
                    next.add(neighbour);
                }
            }
            frontier = next;
        }
        // 깊이 상한까지 돌았다. 마지막 세대에 노드가 있다는 것만으로는 잘렸다고 볼 수 없다 —
        // 거기서 더 나가는 간선이 실제로 있어야 잘린 것이다. 한 세대만 더 들여다보고
        // **담지는 않는다**. 이 확인을 빼면 깊이 5에 정확히 끝나는 혈통도 "일부 생략" 으로
        // 표시되고, 사용자는 있지도 않은 이력을 찾게 된다.
        return new Traversal(hasUnexploredNeighbour(frontier, depthById, direction));
    }

    /** 마지막 세대에서 더 나갈 곳이 남았는가. 노드를 추가하지 않고 존재만 확인한다. */
    private boolean hasUnexploredNeighbour(
            Deque<Long> frontier, Map<Long, Integer> depthById, Direction direction) {
        for (Long materialId : frontier) {
            for (MaterialLineage edge : read(materialId, direction)) {
                Long neighbour = direction == Direction.ANCESTORS
                        ? edge.getParentMaterialId()
                        : edge.getChildMaterialId();
                if (!depthById.containsKey(neighbour)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<MaterialLineage> read(Long materialId, Direction direction) {
        return direction == Direction.ANCESTORS
                ? materialLineageRepository.findAllByChildMaterialId(materialId)
                : materialLineageRepository.findAllByParentMaterialId(materialId);
    }

    /** 노드 메타데이터는 한 번에 읽는다. 소재마다 findById 를 부르면 그게 N+1 이다. */
    private List<MaterialLineageResponse.Node> toNodes(Map<Long, Integer> depthById) {
        Map<Long, Material> materials = materialRepository.findAllById(depthById.keySet()).stream()
                .collect(Collectors.toMap(Material::getId, Function.identity(), (a, b) -> a, HashMap::new));

        List<MaterialLineageResponse.Node> nodes = new ArrayList<>(depthById.size());
        for (Map.Entry<Long, Integer> entry : depthById.entrySet()) {
            Material material = materials.get(entry.getKey());
            if (material == null) {
                // FK 가 있으므로 정상 데이터에서는 생기지 않는다. 그래도 건너뛴다 —
                // 혈통 하나 때문에 상세 조회 전체가 500 이 되는 것보다 낫다.
                continue;
            }
            nodes.add(new MaterialLineageResponse.Node(
                    material.getId(),
                    material.getMaterialCode(),
                    material.getMaterialType(),
                    material.getRootOrigin(),
                    material.getGenerationType(),
                    material.getName(),
                    material.getStatus(),
                    entry.getValue()));
        }
        return nodes;
    }

    private List<MaterialLineageResponse.Edge> toEdges(Iterable<MaterialLineage> found) {
        List<MaterialLineageResponse.Edge> edges = new ArrayList<>();
        for (MaterialLineage edge : found) {
            edges.add(new MaterialLineageResponse.Edge(
                    edge.getParentMaterialId(),
                    edge.getChildMaterialId(),
                    edge.getRelationType(),
                    edge.getReferenceId()));
        }
        return edges;
    }
}
