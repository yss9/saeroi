package com.ssafy.upcycle.material.service;

import com.ssafy.upcycle.material.entity.MaterialLineage;
import com.ssafy.upcycle.material.entity.MaterialLineageRelationType;
import com.ssafy.upcycle.material.repository.MaterialLineageRepository;
import com.ssafy.upcycle.processing.entity.ProcessType;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 한 소재가 <b>지금까지 거친 가공 종류</b>. 혈통을 거슬러 올라가며 모은다.
 *
 * <h2>왜 소재에 컬럼으로 두지 않는가</h2>
 *
 * <p>가공 이력은 소재의 속성이 아니라 <b>혈통에서 따라 나오는 값</b>이다. 컬럼에 적어 두면
 * 소재를 만드는 모든 경로(가공 승인·자투리 발행·거래 이관)가 부모의 값을 복사해 이어붙일
 * 책임을 나눠 갖게 되고, 그중 하나가 빠지면 이력이 조용히 끊긴다 — 그리고 그건 나중에
 * 어느 쪽이 맞는지 판단할 근거가 없다. {@code MaterialVerificationState} 를 조회 시점에
 * 계산하는 것과 같은 이유다.
 *
 * <h2>무엇을 모으는가</h2>
 *
 * <pre>
 *   폐데님(INITIAL)  --가공 #7(세척,해체)-->  데님조각  --가공 #9(선별)-->  선별데님
 *
 *   선별데님.completedProcessTypes = [CLEANING, DISASSEMBLY, SORTING]
 * </pre>
 *
 * <p>간선을 위로 읽으면 "이 소재는 저 작업에서 나왔다" 이므로, 조상으로 올라가며 만나는
 * {@code PROCESSING} 간선의 작업들이 곧 이 소재의 가공 이력이다.
 *
 * <p>{@code PRODUCTION_SCRAP} 간선도 <b>따라 올라간다</b>. 자투리는 부모가 거친 가공을 이미
 * 거친 상태로 떨어져 나온 것이라, 여기서 끊으면 같은 소재가 자투리가 되는 순간 이력이
 * 사라진다. 다만 그 간선 자체는 가공이 아니므로 종류를 더하지는 않는다.
 *
 * <h2>비용 — 쿼리는 세대 수 + 1</h2>
 *
 * <p>여러 소재를 한 번에 받아 <b>세대별로 한 번씩</b> 조회한다. 소재마다 따로 올라가면
 * 목록 길이 × 세대 수만큼 쿼리가 나간다.
 *
 * <p>위로 가는 길은 갈라지지 않는다 — {@code MaterialLineageRegistrar} 가 *"부모는 항상
 * 하나"* 를 강제하므로 조상 탐색은 트리가 아니라 사슬이고, 소재당 최대
 * {@value #MAX_DEPTH} 개다. 그래서 {@code MaterialLineageQueryService} 가 두는 노드 상한이
 * 여기에는 필요 없다. 그래도 깊이 상한은 둔다: 그 규칙이 깨진 데이터가 들어오면
 * 상한이 없는 쪽은 멈추지 않는다.
 */
@Service
@RequiredArgsConstructor
public class MaterialProcessHistoryResolver {

    /**
     * 거슬러 올라갈 최대 세대 수.
     *
     * <p>{@code 02-domain-rules.md} 가 정한 값이고 {@code MaterialLineageQueryService} 와
     * 같다 — 회수를 제외해 순환이 없고 현실적으로 2~3세대다. 상수를 공유하지 않는 이유는
     * 저쪽은 <b>화면에 그릴 그래프</b>의 상한이고 이쪽은 <b>판정 근거</b>의 상한이라,
     * 한쪽을 넓힐 때 다른 쪽이 따라 움직이면 안 되기 때문이다.
     */
    static final int MAX_DEPTH = 5;

    private final MaterialLineageRepository lineageRepository;
    private final ProcessingHistory processingHistory;

    /** 소재 하나의 가공 이력. 목록이 아니라 상세 조회용이다. */
    @Transactional(readOnly = true)
    public List<ProcessType> resolve(Long materialId) {
        return resolveAll(List.of(materialId)).getOrDefault(materialId, List.of());
    }

    /**
     * 여러 소재의 가공 이력을 한 번에.
     *
     * <p>가공을 거치지 않은 소재는 <b>결과 맵에 빈 목록으로</b> 들어간다. 호출부가
     * {@code getOrDefault} 를 잊어도 {@code null} 이 화면까지 가지 않게 한다.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<ProcessType>> resolveAll(Collection<Long> materialIds) {
        Set<Long> roots = new LinkedHashSet<>();
        for (Long materialId : materialIds) {
            if (materialId != null) {
                roots.add(materialId);
            }
        }
        if (roots.isEmpty()) {
            return Map.of();
        }

        Map<Long, Set<Long>> jobsByRoot = new HashMap<>();
        Map<Long, Set<Long>> visitedByRoot = new HashMap<>();
        // 지금 보고 있는 소재들과, 그 소재에 도달한 출발점들. 한 소재가 여러 출발점의
        // 조상일 수 있어(같은 폐데님에서 갈라진 두 소재) 값이 집합이다.
        Map<Long, Set<Long>> frontier = new LinkedHashMap<>();
        for (Long root : roots) {
            jobsByRoot.put(root, new LinkedHashSet<>());
            visitedByRoot.put(root, new HashSet<>(Set.of(root)));
            frontier.put(root, new LinkedHashSet<>(Set.of(root)));
        }

        for (int depth = 0; depth < MAX_DEPTH && !frontier.isEmpty(); depth++) {
            Map<Long, Set<Long>> next = new LinkedHashMap<>();
            for (MaterialLineage edge : lineageRepository.findAllByChildMaterialIdIn(frontier.keySet())) {
                Set<Long> reached = frontier.get(edge.getChildMaterialId());
                if (reached == null) {
                    continue;
                }
                for (Long root : reached) {
                    if (edge.getRelationType() == MaterialLineageRelationType.PROCESSING) {
                        jobsByRoot.get(root).add(edge.getReferenceId());
                    }
                    if (visitedByRoot.get(root).add(edge.getParentMaterialId())) {
                        next.computeIfAbsent(edge.getParentMaterialId(), key -> new LinkedHashSet<>())
                                .add(root);
                    }
                }
            }
            frontier = next;
        }

        Set<Long> allJobs = new LinkedHashSet<>();
        jobsByRoot.values().forEach(allJobs::addAll);
        Map<Long, Set<ProcessType>> typesByJob =
                processingHistory.findConfirmedProcessTypes(allJobs);

        Map<Long, List<ProcessType>> byMaterial = new LinkedHashMap<>();
        for (Long root : roots) {
            // 열거 순서(세척 → 해체 → 선별 → 재단 …)로 내려간다. 작업 여러 건을 합집합으로
            // 접는 순간 "어느 작업이 먼저였나" 는 남지 않으므로, 남는 순서는 공정 순서에
            // 가까운 선언 순서가 낫다 — 화면이 매번 다시 정렬하지 않아도 된다.
            EnumSet<ProcessType> types = EnumSet.noneOf(ProcessType.class);
            for (Long jobId : jobsByRoot.get(root)) {
                types.addAll(typesByJob.getOrDefault(jobId, Set.of()));
            }
            byMaterial.put(root, List.copyOf(types));
        }
        return byMaterial;
    }
}
