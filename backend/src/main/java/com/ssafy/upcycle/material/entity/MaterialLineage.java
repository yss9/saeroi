package com.ssafy.upcycle.material.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 부모 소재와 자식 소재를 잇는 혈통 간선 한 개. Processing 과 Production 이 확정될 때 생긴다.
 *
 * <p><b>왜 별도 테이블인가.</b> {@link Material} 에 {@code parent_material_id} 컬럼 하나만
 * 두면 1:N 은 되지만 N:1 이 안 된다.
 *
 * <pre>
 *   1 -> N  해체
 *   MAT-001 폐청바지  ->  MAT-002 데님 / MAT-003 안감 / MAT-004 금속
 *
 *   N -> 1  혼합·재생
 *   MAT-001 데님 + MAT-010 데님  ->  MAT-020 재생원단
 * </pre>
 *
 * 간선을 행으로 두면 두 방향 모두, 나아가 N:N 까지 표현된다.
 *
 * <p>UNIQUE {@code (child_material_id, parent_material_id, reference_id)} 가 걸려 있어
 * 같은 작업에서 같은 부모-자식 쌍이 두 번 기록되지 않는다. reference_id 가 키에 포함되므로
 * <b>다른</b> 작업에서 같은 쌍이 다시 이어지는 것은 허용된다.
 */
@Getter
@Entity
@Table(name = "material_lineage")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class MaterialLineage {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "child_material_id", nullable = false)
    private Long childMaterialId;

    @Column(name = "parent_material_id", nullable = false)
    private Long parentMaterialId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false)
    private MaterialLineageRelationType relationType;

    /** PROCESSING 이면 processing_job 의 PK, PRODUCTION_SCRAP 이면 production_batch 의 PK. */
    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private MaterialLineage(
            Long childMaterialId,
            Long parentMaterialId,
            MaterialLineageRelationType relationType,
            Long referenceId) {
        if (childMaterialId == null) {
            throw new IllegalArgumentException("Material lineage child material id must not be null");
        }
        if (parentMaterialId == null) {
            throw new IllegalArgumentException("Material lineage parent material id must not be null");
        }
        if (childMaterialId.equals(parentMaterialId)) {
            throw new IllegalArgumentException("Material lineage must not link a material to itself");
        }
        if (relationType == null) {
            throw new IllegalArgumentException("Material lineage relation type must not be null");
        }
        if (referenceId == null) {
            throw new IllegalArgumentException("Material lineage reference id must not be null");
        }
        this.childMaterialId = childMaterialId;
        this.parentMaterialId = parentMaterialId;
        this.relationType = relationType;
        this.referenceId = referenceId;
    }

    /**
     * 혈통 간선을 만든다. 자기 자신을 부모로 두는 것은 막는다 — 혈통 조회가 무한히 도는
     * 사이클의 가장 짧은 형태이고, schema 도 CHECK 로 같은 조건을 건다.
     */
    public static MaterialLineage of(
            Long childMaterialId,
            Long parentMaterialId,
            MaterialLineageRelationType relationType,
            Long referenceId) {
        return new MaterialLineage(childMaterialId, parentMaterialId, relationType, referenceId);
    }
}
