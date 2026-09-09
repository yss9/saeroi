package com.ssafy.upcycle.evidence.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.common.config.ClockConfig;
import com.ssafy.upcycle.common.jpa.config.JpaAuditingConfig;
import com.ssafy.upcycle.common.storage.FileContent;
import com.ssafy.upcycle.common.storage.FileStorageProperties;
import com.ssafy.upcycle.common.storage.FileStorageService;
import com.ssafy.upcycle.common.storage.LocalFileStorageService;
import com.ssafy.upcycle.evidence.dto.EvidenceFileResponse;
import com.ssafy.upcycle.evidence.dto.EvidenceUploadRequest;
import com.ssafy.upcycle.evidence.entity.EvidenceFile;
import com.ssafy.upcycle.evidence.entity.EvidenceReferenceType;
import com.ssafy.upcycle.evidence.entity.EvidenceType;
import com.ssafy.upcycle.evidence.repository.EvidenceFileRepository;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Evidence Hash 무결성. S15P21E104-167.
 *
 * <p><b>이 테스트가 고정하는 것.</b> {@code evidence_file.file_hash} 는 온체인
 * {@code MaterialToken.verifyEvidence} 가 대조할 값이다 ({@code ERD.md} Evidence 절). 그 값이
 * 실제 파일 바이트에서 온 것이 아니면 온체인 대조는 처음부터 의미가 없다.
 *
 * <h2>서버가 클라이언트 해시를 신뢰하는가 — 신뢰하지 않는다</h2>
 *
 * <p>{@code EvidenceUploadRequest} 에는 해시 필드가 아예 없다 (업로드 폼은 파일과 참조만
 * 받는다). 해시는 {@code LocalFileStorageService#store} 가 바이트를 디스크에 쓰는 스트림을
 * {@code DigestOutputStream} 으로 감싸며 서버가 직접 계산한다. 저장소 계층의 값-정확성은
 * 이미 {@code LocalFileStorageServiceTests#hashesTheBytesThatWereActuallyWritten} 이 고정하고
 * 있다 — 여기서는 <b>Evidence 도메인 계층까지 그 값이 그대로 전달·영속화되는지</b>와,
 * <b>업로드~읽기 왕복에서 해시가 실제 바이트와 계속 일치하는지</b>를 본다.
 *
 * <h2>변조 탐지는 능동적이지 않다 — 이 테스트가 그 사실을 명시한다</h2>
 *
 * <p>저장소 바이트가 업로드 이후 변조되면(디스크 손상, 수동 수정 등) 재계산한 해시는
 * DB 의 {@code file_hash} 와 달라진다 — 그런데 {@code EvidenceContentService#read} 는 그 대조를
 * 하지 않는다. 읽기는 그저 저장 키가 가리키는 바이트를 그대로 흘려보낼 뿐이다. 이 클래스의
 * 마지막 테스트는 "달라짐을 계산으로 확인할 수 있다" 는 것과 "그 확인을 서버가 자동으로
 * 하지는 않는다" 는 것을 함께 못박는다 — 후자를 프로덕션 코드로 조용히 고치지 않는다.
 * 온체인 {@code verifyEvidence} 로 이 대조를 하는 경로는 백엔드에 아직 연결되어 있지 않다
 * ({@code EvidenceAnchorPolicy} 주석 참고, MR 설명에도 명시).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    JpaAuditingConfig.class,
    ClockConfig.class,
    EvidenceUploadService.class,
    EvidenceReferenceValidator.class,
    EvidenceAccessPolicy.class,
    EvidenceAnchorPolicy.class,
    EvidenceContentService.class,
    EvidenceHashIntegrityTests.StorageTestConfig.class
})
class EvidenceHashIntegrityTests {

    private static final byte[] CONTENT_A = "photo-bytes-a".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CONTENT_B = "photo-bytes-b-different".getBytes(StandardCharsets.UTF_8);
    private static final String HOLDER_ADDRESS = "0x4444444444444444444444444444444444444444";

    @TempDir
    static Path tempDir;

    @org.springframework.boot.test.context.TestConfiguration
    static class StorageTestConfig {
        @org.springframework.context.annotation.Bean
        FileStorageService fileStorageService() {
            return new LocalFileStorageService(
                    FileStorageProperties.local(tempDir.toString()), Clock.systemDefaultZone());
        }
    }

    @Autowired
    private EvidenceUploadService uploadService;

    @Autowired
    private EvidenceContentService contentService;

    @Autowired
    private EvidenceFileRepository evidenceFileRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Long uploaderId;
    private Long materialId;

    @BeforeEach
    void setUp() throws Exception {
        // 앞 테스트가 쓴 파일이 남으면 왕복 검증이 엉뚱한 바이트를 대조하게 된다.
        try (Stream<Path> walk = Files.walk(tempDir)) {
            walk.filter(Files::isRegularFile).forEach(file -> {
                try {
                    Files.delete(file);
                } catch (Exception ignored) {
                    // 지우지 못하면 아래 단언이 실패해 드러난다.
                }
            });
        }

        User uploader = entityManager.persistAndFlush(User.register(
                "hash-uploader@example.com", "{noop}x", "공급자", "010-0000-0000", UserRole.SUPPLIER));
        uploaderId = uploader.getId();
        materialId = entityManager
                .persistAndFlush(Material.register(
                        "MAT-000950",
                        MaterialType.DENIM,
                        MaterialRootOrigin.WASTE,
                        "폐데님 원단",
                        null,
                        "B",
                        10_000L,
                        null,
                        null,
                        uploaderId))
                .getId();
        entityManager.persistAndFlush(
                MaterialBalance.of(materialId, uploaderId, HOLDER_ADDRESS, 1_000L));
    }

    private EvidenceFileResponse upload(byte[] content, String filename) {
        return uploadService
                .upload(
                        uploaderId,
                        new EvidenceUploadRequest(
                                EvidenceReferenceType.MATERIAL, materialId, EvidenceType.BEFORE_PHOTO),
                        List.of(new MockMultipartFile("files", filename, "image/jpeg", content)))
                .get(0);
    }

    private static String sha256Of(byte[] content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return "0x" + HexFormat.of().formatHex(digest.digest(content));
    }

    // ── 값 정확성 (업로드 → 영속화) ─────────────────────────

    @Test
    void thePersistedHashIsTheActualSha256OfTheUploadedBytes() throws Exception {
        EvidenceFileResponse uploaded = upload(CONTENT_A, "before.jpg");

        String expected = sha256Of(CONTENT_A);
        assertThat(uploaded.fileHash()).isEqualTo(expected);

        EvidenceFile persisted = evidenceFileRepository.findById(uploaded.evidenceFileId()).orElseThrow();
        assertThat(persisted.getFileHash())
                .as("응답과 DB 행이 같은 해시를 가리켜야 한다")
                .isEqualTo(expected);
    }

    @Test
    void differentBytesProduceDifferentHashes() {
        EvidenceFileResponse first = upload(CONTENT_A, "a.jpg");
        EvidenceFileResponse second = upload(CONTENT_B, "b.jpg");

        assertThat(first.fileHash()).isNotEqualTo(second.fileHash());
    }

    @Test
    void identicalBytesProduceIdenticalHashesAcrossSeparateUploads() {
        // 같은 내용을 두 번 올리면(예: 재전송) 해시는 같아야 한다 — 해시가 업로드
        // 시각이나 저장 키가 아니라 오직 바이트에서만 온다는 것을 확인한다.
        EvidenceFileResponse first = upload(CONTENT_A, "a1.jpg");
        EvidenceFileResponse second = upload(CONTENT_A, "a2.jpg");

        assertThat(first.fileHash()).isEqualTo(second.fileHash());
        assertThat(first.storageKey())
                .as("저장 키는 매번 새로 발급된다 — 해시가 같다고 같은 행을 가리키지 않는다")
                .isNotEqualTo(second.storageKey());
    }

    // ── 왕복 무결성 (업로드 → 읽기) ─────────────────────────

    @Test
    void theHashRecordedAtUploadMatchesTheBytesReturnedByRead() throws Exception {
        EvidenceFileResponse uploaded = upload(CONTENT_A, "before.jpg");

        byte[] readBack;
        try (InputStream stream = contentService.read(uploaderId, uploaded.evidenceFileId()).stream()) {
            readBack = stream.readAllBytes();
        }

        assertThat(sha256Of(readBack))
                .as("업로드 시점 해시와 읽기 시점 바이트의 해시가 왕복 후에도 일치해야 한다")
                .isEqualTo(uploaded.fileHash());
        assertThat(readBack).isEqualTo(CONTENT_A);
    }

    // ── 변조 탐지 — 계산은 가능하나 서버가 자동으로 하지 않는다 ──

    @Test
    void aTamperedFileNoLongerMatchesThePersistedHashButTheServerDoesNotNoticeOnRead()
            throws Exception {
        EvidenceFileResponse uploaded = upload(CONTENT_A, "before.jpg");
        Path writtenFile = tempDir.resolve(uploaded.storageKey());

        // 디스크의 바이트를 직접 바꾼다 — 손상·변조를 흉내낸다. DB 의 file_hash 는
        // 그대로 CONTENT_A 의 해시다.
        Files.write(writtenFile, CONTENT_B);

        byte[] tamperedBytes;
        try (InputStream stream = contentService.read(uploaderId, uploaded.evidenceFileId()).stream()) {
            tamperedBytes = stream.readAllBytes();
        }

        assertThat(tamperedBytes)
                .as("read() 는 변조된 바이트를 그대로 돌려준다 — 대조하지 않는다")
                .isEqualTo(CONTENT_B);
        assertThat(sha256Of(tamperedBytes))
                .as("재계산한 해시는 DB 에 저장된 값과 더 이상 일치하지 않는다 — "
                        + "이 불일치를 사람이나 별도 배치가 알아채야 한다. "
                        + "EvidenceContentService#read 는 이것을 검사하지 않는다")
                .isNotEqualTo(uploaded.fileHash());

        EvidenceFile persisted =
                evidenceFileRepository.findById(uploaded.evidenceFileId()).orElseThrow();
        assertThat(persisted.getFileHash())
                .as("행은 업로드 당시의 해시를 그대로 들고 있다 — 변조 후에도 갱신되지 않는다")
                .isEqualTo(uploaded.fileHash());
    }

    // ── 형식 (온체인 대조 가능 여부) ─────────────────────────

    @Test
    void thePersistedHashHasTheOnChainComparableFormat() {
        EvidenceFileResponse uploaded = upload(CONTENT_A, "before.jpg");

        assertThat(uploaded.fileHash())
                .as("evidence_file.file_hash CHAR(66) · 0x + SHA-256 64자")
                .hasSize(66)
                .startsWith("0x")
                .matches("0x[0-9a-f]{64}");
    }
}
