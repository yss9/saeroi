package com.ssafy.upcycle.material.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.MaterialErrorCode;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialLineage;
import com.ssafy.upcycle.material.entity.MaterialLineageRelationType;
import com.ssafy.upcycle.material.entity.MaterialEventReferenceType;
import com.ssafy.upcycle.material.entity.MaterialEventType;
import com.ssafy.upcycle.material.repository.MaterialLineageRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 부모-자식 소재 관계를 기록하는 유일한 지점.
 *
 * <p><b>왜 서비스가 필요한가.</b> {@code MaterialLineage} 엔티티는 간선 하나의 규칙만 안다 —
 * null 금지, 자기 참조 금지. 그런데 혈통에는 <b>두 소재를 함께 봐야 아는 규칙</b>이 있고,
 * 그건 엔티티가 판단할 수 없다. 이 클래스가 그 자리다.
 *
 * <p>Processing 확정(4번)과 Production 확정(5번)이 자식 소재를 만들 때 이 클래스를 부른다.
 * 각자 {@code MaterialLineage.of(...)} 를 직접 부르면 아래 규칙이 도메인마다 따로 구현되고,
 * 그중 하나가 빠지면 혈통 그래프가 조용히 오염된다 — 그리고 혈통은 <b>추적성의 근거</b>라
 * 한번 잘못 기록되면 되돌릴 방법이 없다.
 *
 * <h2>여기서 강제하는 규칙</h2>
 *
 * <ol>
 *   <li><b>부모는 항상 하나</b> — {@code 02-domain-rules.md} §2.
 *       *"혼합 가공을 지원하지 않으므로 부모는 항상 하나다. 따라서 root_origin 상속이
 *       모호해지지 않고, 업사이클 비율을 이 값 하나로 판단할 수 있다"*.
 *       N→1 혼합은 MVP 제외이므로, 이미 부모가 있는 자식에 다른 부모를 붙이는 것을 막는다.
 *       <b>테이블 구조는 N:1 을 표현할 수 있다</b> — 막는 것은 정책이고, 정책이 열리면
 *       이 검사만 빼면 된다</li>
 *   <li><b>{@code root_origin} 상속</b> — 같은 문서: *"출처. 자식 생성 시 부모에서 그대로 복사"*.
 *       자식의 {@code root_origin} 이 부모와 다르면 업사이클 비율 판단이 깨진다.
 *       복사를 여기서 하지 않고 <b>검증만</b> 하는 이유는, 자식 소재는 이미 만들어진 뒤에
 *       혈통이 이어지기 때문이다 — 값을 고치면 호출부가 모르는 사이에 소재가 바뀐다</li>
 *   <li><b>중복 간선</b> — UNIQUE {@code (child, parent, reference_id)} 가 DB 에서 잡지만,
 *       그 예외는 사용자에게 설명할 수 없다. 미리 확인해 도메인 오류로 바꾼다</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class MaterialLineageRegistrar {

    private final MaterialRepository materialRepository;
    private final MaterialLineageRepository materialLineageRepository;
    private final MaterialEventRecorder eventRecorder;

    /**
     * 자식 소재에 부모를 잇는다.
     *
     * @param childMaterialId 가공·생산으로 새로 만들어진 소재
     * @param parentMaterialId 투입된 소재
     * @param relationType {@code PROCESSING} 이면 가공, {@code PRODUCTION_SCRAP} 이면 자투리
     * @param referenceId {@code PROCESSING} 이면 {@code processing_job} PK,
     *     {@code PRODUCTION_SCRAP} 이면 {@code production_batch} PK
     */
    @Transactional
    public MaterialLineage link(
            Long childMaterialId,
            Long parentMaterialId,
            MaterialLineageRelationType relationType,
            Long referenceId) {
        Material child = require(childMaterialId);
        Material parent = require(parentMaterialId);

        requireSingleParent(childMaterialId, parentMaterialId, referenceId);
        requireInheritedRootOrigin(child, parent);

        MaterialLineage lineage = materialLineageRepository.save(
                MaterialLineage.of(childMaterialId, parentMaterialId, relationType, referenceId));
        MaterialEventReferenceType referenceType = relationType == MaterialLineageRelationType.PROCESSING
                ? MaterialEventReferenceType.PROCESSING_JOB
                : MaterialEventReferenceType.PRODUCTION_BATCH;
        eventRecorder.record(
                childMaterialId,
                MaterialEventType.MATERIAL_DERIVED,
                child.getRegistrantId(),
                referenceType,
                referenceId,
                parentMaterialId,
                child.getTotalQuantity(),
                "부모 소재에서 파생",
                MaterialEventRecorder.key(
                        MaterialEventType.MATERIAL_DERIVED, childMaterialId, referenceId));
        eventRecorder.record(
                parentMaterialId,
                MaterialEventType.CHILD_MATERIAL_CREATED,
                child.getRegistrantId(),
                referenceType,
                referenceId,
                childMaterialId,
                child.getTotalQuantity(),
                "자식 소재 생성",
                MaterialEventRecorder.key(
                        MaterialEventType.CHILD_MATERIAL_CREATED,
                        parentMaterialId,
                        referenceId,
                        childMaterialId));
        return lineage;
    }

    private Material require(Long materialId) {
        return materialRepository
                .findById(materialId)
                .orElseThrow(() -> new BusinessException(MaterialErrorCode.MATERIAL_NOT_FOUND));
    }

    /**
     * 이미 다른 부모가 있으면 거부한다. 같은 부모·같은 작업이 다시 오면 중복으로 거부한다.
     *
     * <p>같은 부모라도 <b>다른 작업</b>({@code reference_id})으로 다시 이어지는 것은 허용한다 —
     * UNIQUE 키에 {@code reference_id} 가 포함된 이유이고, 자투리를 재투입하는 흐름이 그렇다.
     */
    private void requireSingleParent(Long childMaterialId, Long parentMaterialId, Long referenceId) {
        List<MaterialLineage> existing =
                materialLineageRepository.findAllByChildMaterialId(childMaterialId);
        for (MaterialLineage edge : existing) {
            if (!edge.getParentMaterialId().equals(parentMaterialId)) {
                throw new BusinessException(MaterialErrorCode.MATERIAL_LINEAGE_MULTIPLE_PARENTS);
            }
            if (edge.getReferenceId().equals(referenceId)) {
                throw new BusinessException(MaterialErrorCode.MATERIAL_LINEAGE_DUPLICATE);
            }
        }
    }

    /** 자식이 부모의 {@code root_origin} 을 그대로 물려받았는지 확인한다. */
    private void requireInheritedRootOrigin(Material child, Material parent) {
        if (child.getRootOrigin() != parent.getRootOrigin()) {
            throw new BusinessException(MaterialErrorCode.MATERIAL_LINEAGE_ROOT_ORIGIN_MISMATCH);
        }
    }
}
