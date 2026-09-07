package com.ssafy.upcycle.evidence.entity;

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
 * 업로드된 증빙 파일 한 건. 테이블은 {@code schema.sql} 에 이미 있었고 매핑만 없었다.
 *
 * <h2>이 행은 지우지 않는다</h2>
 *
 * <p>{@code file_hash} 가 온체인에 기록된다. 파일을 지우고 행도 지우면 <b>온체인에는
 * 해시가 남아 있는데 무엇의 해시인지 알 수 없는 상태</b>가 된다. 되돌릴 수 없다.
 * 그래서 이 엔티티에는 삭제·수정 메서드가 없다. 잘못 올린 파일은 새로 올리고
 * 화면이 최신 것을 보여주는 방식으로 다룬다.
 *
 * <h2>reference_id 에 FK 가 없다</h2>
 *
 * <p>대상 테이블이 넷(material · processing_job · production_batch · production_input)
 * 이라 FK 를 걸 수 없다. DB 가 막아 주지 않으므로 <b>대상 존재 확인은 업로드 서비스의
 * 책임</b>이다 — 확인하지 않으면 없는 소재를 가리키는 증빙이 남는다.
 *
 * <h2>file_url 이 아니라 저장 키다</h2>
 *
 * <p>컬럼 이름은 {@code file_url} 이지만 실제로 담는 값은 {@link
 * com.ssafy.upcycle.common.storage.StoredFile#storageKey()} — 저장소 루트 기준 상대
 * 경로다. 절대 URL 을 넣으면 도메인이나 버킷이 바뀔 때 모든 행을 고쳐야 하고,
 * MinIO/S3 로 옮길 때(S15P21E104-162) 그 이관이 실패하면 파일을 찾을 수 없다.
 * 공개 URL 은 조회 시점에 조립한다.
 */
@Getter
@Entity
@Table(name = "evidence_file")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class EvidenceFile {

    private static final int STORAGE_KEY_MAX_LENGTH = 500;
    private static final int FILE_HASH_LENGTH = 66;

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false)
    private EvidenceReferenceType referenceType;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    /** 저장 키. 위 주석 참고 — 컬럼 이름과 담는 값이 다르다. */
    @Column(name = "file_url", nullable = false, length = STORAGE_KEY_MAX_LENGTH)
    private String fileUrl;

    /** {@code 0x} + 64자. 온체인 비교 형식이라 길이를 고정한다. */
    @Column(name = "file_hash", nullable = false, columnDefinition = "CHAR(66)")
    private String fileHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false)
    private EvidenceType evidenceType;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @CreatedDate
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    private EvidenceFile(
            EvidenceReferenceType referenceType,
            Long referenceId,
            String fileUrl,
            String fileHash,
            EvidenceType evidenceType,
            Long uploadedBy) {
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.fileUrl = fileUrl;
        this.fileHash = fileHash;
        this.evidenceType = evidenceType;
        this.uploadedBy = uploadedBy;
    }

    /**
     * 증빙을 기록한다.
     *
     * <p>여기서 막는 것은 <b>온체인에 올라갈 값의 형식</b>이다. 해시 길이가 어긋난 채
     * 저장되면 나중에 온체인 값과 대조할 수 없고, 그때는 파일이 정말 그것인지 확인할
     * 방법이 사라진다. DB {@code CHAR(66)} 은 짧은 값을 공백으로 채워 통과시킨다.
     */
    public static EvidenceFile record(
            EvidenceReferenceType referenceType,
            Long referenceId,
            String storageKey,
            String fileHash,
            EvidenceType evidenceType,
            Long uploadedBy) {
        requireNonNull(referenceType, "reference type");
        requireNonNull(evidenceType, "evidence type");
        requirePositive(referenceId, "reference id");
        requirePositive(uploadedBy, "uploader id");
        requireText(storageKey, "storage key");
        requireHash(fileHash);
        return new EvidenceFile(
                referenceType, referenceId, storageKey, fileHash, evidenceType, uploadedBy);
    }

    private static void requireNonNull(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Evidence " + field + " must not be null");
        }
    }

    private static void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("Evidence " + field + " must be positive");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Evidence " + field + " must not be blank");
        }
        if (value.length() > STORAGE_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("Evidence " + field + " is too long");
        }
    }

    private static void requireHash(String fileHash) {
        if (fileHash == null || fileHash.length() != FILE_HASH_LENGTH) {
            throw new IllegalArgumentException(
                    "Evidence file hash must be 0x + 64 hex characters");
        }
        if (!fileHash.startsWith("0x")) {
            throw new IllegalArgumentException("Evidence file hash must start with 0x");
        }
    }
}
