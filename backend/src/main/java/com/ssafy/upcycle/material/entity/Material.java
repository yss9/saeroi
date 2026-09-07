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

    /** {@code material_code} 컬럼 길이. 임시 코드도 이 안에 들어가야 한다. */
    public static final int MATERIAL_CODE_MAX_LENGTH = 20;

    /**
     * 아직 정식 코드가 붙지 않은 소재의 코드 접두어.
     *
     * <p>{@code material_code} 가 {@code NOT NULL} UNIQUE 라 INSERT 시점에 값이 필요한데
     * 정식 코드는 저장 후 확정되는 {@code id} 에서 파생한다. 그 사이를 메우는 값이며
     * {@link #assignCode} 가 교체 가능 여부를 이 접두어로 판정한다.
     *
     * <p>불변식이 엔티티에 있으므로 접두어도 여기 둔다 — 코드를 만드는 쪽
     * ({@code MaterialCodeGenerator})이 이 상수를 참조한다.
     */
    public static final String TEMPORARY_CODE_PREFIX = "TMP-";

    private static final int NAME_MAX_LENGTH = 200;
    private static final int GRADE_MAX_LENGTH = 50;
    private static final int CONTRACT_ADDRESS_MAX_LENGTH = 42;
    private static final int MANIFEST_URL_MAX_LENGTH = 500;
    private static final int ORIGIN_REASON_NOTE_MAX_LENGTH = 200;

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

    /**
     * 왜 폐기물이 되었나. {@code root_origin=WASTE} 일 때만 의미가 있다.
     *
     * <p>{@code generation_type}("어떻게 생겼나")과 역할이 다르다 —
     * {@link MaterialOriginReason} 주석 참고.
     *
     * <p><b>{@code register} 파라미터가 아니라 {@link #describeOrigin} 으로 따로 받는다.</b>
     * 컬럼이 나중에 추가되어 값이 없는 소재가 이미 존재하고, {@code register} 에 필수
     * 파라미터로 넣으면 소재를 만드는 모든 자리(다른 도메인 테스트 포함)가 이 값을 알아야
     * 한다. 파라미터가 12개가 되면서 nullable 인자가 늘어나는 것도 위치를 헷갈리게 만든다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "origin_reason")
    private MaterialOriginReason originReason;

    /** {@link MaterialOriginReason#ETC} 일 때 필수. 다른 값에서도 부가 설명으로 쓸 수 있다. */
    @Column(name = "origin_reason_note", length = ORIGIN_REASON_NOTE_MAX_LENGTH)
    private String originReasonNote;

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

    /**
     * 저장으로 {@code id} 가 확정된 뒤 정식 {@code material_code} 를 부여한다.
     *
     * <p><b>왜 등록 시점에 못 정하는가.</b> 코드는 DB 가 매기는 {@code id} 에서 파생하는데
     * {@code material_code} 는 {@code NOT NULL} UNIQUE 라 INSERT 시점에 이미 값이 있어야 한다.
     * 그래서 임시 코드로 INSERT 하고 여기서 교체한다 ({@code MaterialCodeGenerator} 참고).
     *
     * <p><b>이미 정식 코드가 붙은 소재는 거부한다.</b> 코드는 사람이 부르는 식별자여서
     * 한 번 나간 뒤 바뀌면 서류·QR·대화가 전부 어긋난다. 임시 코드일 때만 교체를 허용해
     * 재채번 경로를 원천적으로 막는다.
     */
    /**
     * 발생 사유를 기록한다. {@code assignCode} 와 같이 저장 직후 같은 트랜잭션에서 부른다.
     *
     * <p><b>여기서 막는 것은 출처와의 모순이다.</b> 두 값이 따로 들어오면
     * "신소재인데 재고 소진" 같은 행이 조용히 저장된다. DB 로는 막지 못한다 — 두 컬럼에
     * 걸친 조건이라 {@code CHECK} 가 필요하고, 값이 없는 기존 행이 있는 상태에서 제약을
     * 추가하려면 별도 절차가 붙는다.
     *
     * <p><b>폐기물에 사유가 없는 것은 여기서 막지 않는다.</b> 컬럼이 나중에 추가되어
     * 사유 없는 폐기물 소재가 이미 존재하고, 엔티티가 그걸 거절하면 기존 데이터를 다루는
     * 모든 경로가 깨진다. "등록 시에는 필수" 라는 규칙은 API 계약이므로
     * {@code MaterialRegistrationService} 가 본다.
     */
    public void describeOrigin(MaterialOriginReason originReason, String originReasonNote) {
        if (originReason == null && originReasonNote == null) {
            return;
        }
        if (rootOrigin != MaterialRootOrigin.WASTE && originReason != null) {
            throw new IllegalArgumentException("Only waste material can have an origin reason");
        }
        String note =
                requireOptionalText(originReasonNote, "origin reason note", ORIGIN_REASON_NOTE_MAX_LENGTH);
        if (originReason != null && originReason.requiresNote() && (note == null || note.isBlank())) {
            throw new IllegalArgumentException("ETC origin reason requires a note");
        }
        this.originReason = originReason;
        this.originReasonNote = note;
    }

    public void assignCode(String materialCode) {
        String assigned = requireText(materialCode, "material code", MATERIAL_CODE_MAX_LENGTH);
        if (!this.materialCode.startsWith(TEMPORARY_CODE_PREFIX)) {
            throw new IllegalStateException(
                    "Material code is already assigned and must not change: " + this.materialCode);
        }
        this.materialCode = assigned;
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
