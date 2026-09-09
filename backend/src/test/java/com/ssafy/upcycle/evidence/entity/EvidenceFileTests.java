package com.ssafy.upcycle.evidence.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link EvidenceFile#record} 의 해시 형식 검증. S15P21E104-167.
 *
 * <p>이 엔티티에는 지금까지 {@code EvidenceSchemaMappingTests} 만 있었다 — 컬럼·ENUM 순서가
 * schema.sql 과 어긋나지 않는지만 보고, 생성자 검증 자체는 아무도 고정하지 않았다.
 *
 * <p>{@code file_hash} 는 온체인 대조 형식({@code 0x} + SHA-256 64자)이 강제되지 않으면
 * 나중에 온체인 값과 대조할 수 없고, 그때는 파일이 정말 그것인지 확인할 방법이 사라진다
 * ({@link EvidenceFile#record} 클래스 주석 참고). 여기서는 그 형식 강제가 실제로 동작하는지만
 * 본다 — 온체인 비교 자체는 백엔드에 아직 연결된 경로가 없다.
 */
class EvidenceFileTests {

    private static final Long REFERENCE_ID = 1_024L;
    private static final Long UPLOADER_ID = 7L;
    private static final String STORAGE_KEY = "MATERIAL/1024/2026/09/valid-key.jpg";
    private static final String VALID_HASH =
            "0x" + "a".repeat(64);

    private static final String HEX_63 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"; // 63자
    private static final String HEX_65 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"; // 65자

    @ParameterizedTest
    @ValueSource(strings = {HEX_63, HEX_65, ""})
    void rejectsAHashOfTheWrongLength(String hexBody) {
        String malformed = hexBody.isEmpty() ? "" : "0x" + hexBody;

        assertThatThrownBy(() -> EvidenceFile.record(
                        EvidenceReferenceType.MATERIAL,
                        REFERENCE_ID,
                        STORAGE_KEY,
                        malformed,
                        EvidenceType.BEFORE_PHOTO,
                        UPLOADER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0x + 64 hex");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "0X", // 대문자 접두사 — 온체인 대조는 소문자 0x 를 정본으로 삼는다
        "",   // 접두사 없음
    })
    void rejectsAHashWithoutTheZeroXPrefix(String prefix) {
        String withoutPrefix = prefix + "a".repeat(64);

        assertThatThrownBy(() -> EvidenceFile.record(
                        EvidenceReferenceType.MATERIAL,
                        REFERENCE_ID,
                        STORAGE_KEY,
                        withoutPrefix,
                        EvidenceType.BEFORE_PHOTO,
                        UPLOADER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0x");
    }

    @org.junit.jupiter.api.Test
    void rejectsANullHash() {
        assertThatThrownBy(() -> EvidenceFile.record(
                        EvidenceReferenceType.MATERIAL,
                        REFERENCE_ID,
                        STORAGE_KEY,
                        null,
                        EvidenceType.BEFORE_PHOTO,
                        UPLOADER_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @org.junit.jupiter.api.Test
    void acceptsAWellFormedHash() {
        EvidenceFile recorded = EvidenceFile.record(
                EvidenceReferenceType.MATERIAL,
                REFERENCE_ID,
                STORAGE_KEY,
                VALID_HASH,
                EvidenceType.BEFORE_PHOTO,
                UPLOADER_ID);

        assertThat(recorded.getFileHash()).isEqualTo(VALID_HASH);
        assertThat(recorded.isDeleted()).isFalse();
    }
}
