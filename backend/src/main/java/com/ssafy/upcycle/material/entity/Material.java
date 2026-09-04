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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 하나의 Material ID 는 물성·등급·출처가 동일해 어느 부분을 떼어가도 같게 취급 가능한
 * 하나의 Batch 를 의미한다. Balance 방식이 이 동질성을 전제한다 — 품질이 다르면
 * 처음부터 별도 Material 로 등록해야 한다.
 *
 * <p><b>{@code BaseEntity} 를 상속하지 않는다.</b> DB 내부 식별자는 Identity 전략을 사용하고
 * 생성·수정 시각만 이 엔티티에서 직접 감사한다.
 *
 * <p><b>등록자를 연관관계로 두지 않는다.</b> {@code registrant_id} 는 users FK 지만
 * User 엔티티가 아직 없다. 지금은 FK 값만 들고, User 도메인이 들어온 뒤 승격 여부를 다시 본다.
 *
 * <p>수량은 모두 <b>gram 정수</b>다. 화면의 kg 표기와 소수점 변환은 표현 계층 책임이며,
 * 이 엔티티는 정수만 다룬다.
 */
@Getter
@Entity
@Table(name = "material")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Material {

    private static final int MATERIAL_CODE_MAX_LENGTH = 20;
    private static final int NAME_MAX_LENGTH = 200;
    private static final int GRADE_MAX_LENGTH = 50;
    private static final int CONTRACT_ADDRESS_MAX_LENGTH = 42;
    private static final int MANIFEST_URL_MAX_LENGTH = 500;

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "material_code", nullable = false, length = MATERIAL_CODE_MAX_LENGTH, unique = true)
    private String materialCode;

    @Column(name = "token_id", unique = true)
    private Long tokenId;

    @Column(name = "chain_id")
    private Long chainId;

    @Column(name = "contract_address", length = CONTRACT_ADDRESS_MAX_LENGTH)
    private String contractAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "material_type", nullable = false, length = 50)
    private MaterialType materialType;

    @Enumerated(EnumType.STRING)
    @Column(name = "root_origin", nullable = false)
    private MaterialRootOrigin rootOrigin;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_type", nullable = false)
    private MaterialGenerationType generationType;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "grade", length = GRADE_MAX_LENGTH)
    private String grade;

    /** 총 발행 수량 (gram). */
    @Column(name = "total_quantity", nullable = false)
    private long totalQuantity;

    /** 참고 Metadata. 수량 검증 기준으로 쓰지 않는다. */
    @Column(name = "length_mm")
    private Long lengthMm;

    /** 참고 Metadata. 수량 검증 기준으로 쓰지 않는다. */
    @Column(name = "width_mm")
    private Long widthMm;

    @Column(name = "registrant_id", nullable = false)
    private Long registrantId;

    @Column(name = "evidence_hash", columnDefinition = "CHAR(66)")
    private String evidenceHash;

    @Column(name = "manifest_url", length = MANIFEST_URL_MAX_LENGTH)
    private String manifestUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MaterialStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * schema.sql 의 CHECK 제약을 그대로 미러링한다. 등록 시점에만 성립하는 규칙은
     * {@link #register} 가 따로 본다.
     */
    private Material(
            String materialCode,
            MaterialType materialType,
            MaterialRootOrigin rootOrigin,
            MaterialGenerationType generationType,
            String name,
            String description,
            String grade,
            long totalQuantity,
            Long lengthMm,
            Long widthMm,
            Long registrantId) {
        this.materialCode = requireText(materialCode, "material code", MATERIAL_CODE_MAX_LENGTH);
        this.materialType = requireNonNull(materialType, "material type");
        this.rootOrigin = requireNonNull(rootOrigin, "root origin");
        this.generationType = requireNonNull(generationType, "generation type");
        this.name = requireText(name, "material name", NAME_MAX_LENGTH);
        this.description = description;
        this.grade = requireOptionalText(grade, "material grade", GRADE_MAX_LENGTH);
        if (totalQuantity < 0) {
            throw new IllegalArgumentException("Material total quantity must not be negative");
        }
        this.totalQuantity = totalQuantity;
        this.lengthMm = requireOptionalPositive(lengthMm, "material length");
        this.widthMm = requireOptionalPositive(widthMm, "material width");
        this.registrantId = requireNonNull(registrantId, "registrant id");
        this.status = MaterialStatus.ACTIVE;
    }

    /**
     * 공급자가 플랫폼에 소재를 최초로 올리는 경로.
     *
     * <p>{@code generation_type} 은 {@link MaterialGenerationType#INITIAL} 로 고정한다.
     * PROCESSED 와 PRODUCTION_SCRAP 은 Processing·Production 확정이 만들어내는 값이라
     * 등록 요청이 지정할 수 있는 값이 아니다.
     */
    public static Material register(
            String materialCode,
            MaterialType materialType,
            MaterialRootOrigin rootOrigin,
            String name,
            String description,
            String grade,
            long totalQuantity,
            Long lengthMm,
            Long widthMm,
            Long registrantId) {
        if (totalQuantity <= 0) {
            throw new IllegalArgumentException("Material total quantity must be positive");
        }
        return new Material(
                materialCode,
                materialType,
                rootOrigin,
                MaterialGenerationType.INITIAL,
                name,
                description,
                grade,
                totalQuantity,
                lengthMm,
                widthMm,
                registrantId);
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Material " + field + " must not be null");
        }
        return value;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Material " + field + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Material " + field + " must not exceed " + maxLength + " characters");
        }
        return value;
    }

    private static String requireOptionalText(String value, String field, int maxLength) {
        if (value == null) {
            return null;
        }
        return requireText(value, field, maxLength);
    }

    private static Long requireOptionalPositive(Long value, String field) {
        if (value == null) {
            return null;
        }
        if (value <= 0) {
            throw new IllegalArgumentException("Material " + field + " must be positive when present");
        }
        return value;
    }
}
