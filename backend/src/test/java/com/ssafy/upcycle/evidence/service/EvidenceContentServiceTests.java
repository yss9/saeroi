package com.ssafy.upcycle.evidence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.upcycle.common.config.ClockConfig;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.common.exception.ErrorCode;
import com.ssafy.upcycle.common.jpa.config.JpaAuditingConfig;
import com.ssafy.upcycle.common.storage.FileContent;
import com.ssafy.upcycle.common.storage.FileStorageErrorCode;
import com.ssafy.upcycle.common.storage.FileStorageProperties;
import com.ssafy.upcycle.common.storage.FileStorageService;
import com.ssafy.upcycle.common.storage.LocalFileStorageService;
import com.ssafy.upcycle.evidence.EvidenceApiPaths;
import com.ssafy.upcycle.evidence.EvidenceErrorCode;
import com.ssafy.upcycle.evidence.dto.EvidenceFileResponse;
import com.ssafy.upcycle.evidence.dto.EvidenceUploadRequest;
import com.ssafy.upcycle.evidence.entity.EvidenceReferenceType;
import com.ssafy.upcycle.evidence.entity.EvidenceType;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
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
 * 증빙 파일 조회. S15P21E104-162.
 *
 * <p><b>고정하는 것은 인가다.</b> 증빙에는 거래 상대·중량·공장 내부가 찍혀 있다.
 * 로그인만 하면 아무 id 나 넣어 남의 증빙을 훑을 수 있으면 그건 유출이고,
 * presigned URL 을 쓰지 않은 이유가 정확히 이 판정을 붙일 자리가 없기 때문이다.
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
    EvidenceDeletionService.class,
    EvidenceContentService.class,
    EvidenceContentServiceTests.StorageTestConfig.class
})
class EvidenceContentServiceTests {

    private static final byte[] JPEG = "photo-bytes".getBytes(StandardCharsets.UTF_8);
    private static final String HOLDER_ADDRESS = "0x3333333333333333333333333333333333333333";

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
    private EvidenceDeletionService deletionService;

    @Autowired
    private TestEntityManager entityManager;

    private Long uploaderId;
    private Long registrantId;
    private Long holderId;
    private Long strangerId;
    private Long materialId;

    @BeforeEach
    void setUp() throws Exception {
        try (Stream<Path> walk = Files.walk(tempDir)) {
            walk.filter(Files::isRegularFile).forEach(file -> {
                try {
                    Files.delete(file);
                } catch (Exception ignored) {
                    // 지우지 못하면 아래 단언이 실패해 드러난다.
                }
            });
        }

        registrantId = persistUser("read-registrant@example.com", UserRole.SUPPLIER);
        uploaderId = persistUser("read-uploader@example.com", UserRole.PROCESSOR);
        holderId = persistUser("read-holder@example.com", UserRole.BRAND);
        strangerId = persistUser("read-stranger@example.com", UserRole.BRAND);

        materialId = entityManager
                .persistAndFlush(Material.register(
                        "MAT-000902",
                        MaterialType.DENIM,
                        MaterialRootOrigin.WASTE,
                        "폐데님 원단",
                        null,
                        "B",
                        10_000L,
                        null,
                        null,
                        registrantId))
                .getId();
        entityManager.persistAndFlush(
                MaterialBalance.of(materialId, holderId, HOLDER_ADDRESS, 1_000L));
    }

    private Long persistUser(String email, UserRole role) {
        return entityManager
                .persistAndFlush(
                        User.register(email, "{noop}x", "사용자", "010-0000-0000", role))
                .getId();
    }

    private EvidenceFileResponse upload() {
        return uploadService
                .upload(
                        uploaderId,
                        new EvidenceUploadRequest(
                                EvidenceReferenceType.MATERIAL,
                                materialId,
                                EvidenceType.WEIGHT_SLIP),
                        List.of(new MockMultipartFile("files", "slip.jpg", "image/jpeg", JPEG)))
                .get(0);
    }

    private static ErrorCode errorCodeOf(Throwable failure) {
        return ((BusinessException) failure).getErrorCode();
    }

    /**
     * 바이트를 읽고 <b>스트림을 닫는다.</b>
     *
     * <p>서비스가 닫지 않는 것은 의도된 계약이다 — 닫으면 컨트롤러가 응답으로 흘리기 전에
     * 닫혀 빈 본문이 나간다. 그래서 받은 쪽이 닫아야 하고, 테스트에서 안 닫으면 Windows 는
     * 열린 파일을 지우지 못해 {@code @TempDir} 정리와 다음 테스트가 깨진다.
     */
    private byte[] readBytes(Long requesterId, Long evidenceFileId) throws Exception {
        FileContent content = contentService.read(requesterId, evidenceFileId);
        try (InputStream stream = content.stream()) {
            return stream.readAllBytes();
        }
    }

    // ── 정상 경로 ────────────────────────────────────────────

    @Test
    void streamsTheStoredBytesToTheUploader() throws Exception {
        EvidenceFileResponse uploaded = upload();

        assertThat(readBytes(uploaderId, uploaded.evidenceFileId())).isEqualTo(JPEG);
    }

    @Test
    void reportsTheSizeAndTheMediaTypeDerivedFromTheKey() throws Exception {
        EvidenceFileResponse uploaded = upload();

        FileContent content = contentService.read(uploaderId, uploaded.evidenceFileId());
        try (InputStream stream = content.stream()) {
            assertThat(stream).isNotNull();
            assertThat(content.size()).isEqualTo(JPEG.length);
            assertThat(content.contentType())
                    .as("저장소 메타데이터가 아니라 키의 확장자에서 파생한다 — 두 구현의 답이 같아야 한다")
                    .isEqualTo("image/jpeg");
        }
    }

    @Test
    void theRegistrantAndTheCurrentHolderCanRead() throws Exception {
        EvidenceFileResponse uploaded = upload();

        assertThat(readBytes(registrantId, uploaded.evidenceFileId())).isEqualTo(JPEG);
        // 받은 소재의 증빙을 확인 못 하면 추적성이라는 제품의 목적이 성립하지 않는다.
        assertThat(readBytes(holderId, uploaded.evidenceFileId())).isEqualTo(JPEG);
    }

    @Test
    void theResponseCarriesThePathThatActuallyServesTheFile() {
        EvidenceFileResponse uploaded = upload();

        assertThat(uploaded.contentPath())
                .as("프론트가 경로를 조립하면 규칙이 두 곳에 생긴다")
                .isEqualTo("/api/evidence/" + uploaded.evidenceFileId() + "/content")
                .isEqualTo(EvidenceApiPaths.content(uploaded.evidenceFileId()));
        assertThat(uploaded.storageKey())
                .as("storageKey 는 저장소 키다. 이것으로 파일을 받을 수 없다")
                .startsWith("MATERIAL/" + materialId + "/");
    }

    // ── 인가 ────────────────────────────────────────────────

    @Test
    void refusesAThirdParty() {
        EvidenceFileResponse uploaded = upload();

        assertThatThrownBy(() -> contentService.read(strangerId, uploaded.evidenceFileId()))
                .isInstanceOf(BusinessException.class)
                .extracting(EvidenceContentServiceTests::errorCodeOf)
                .isEqualTo(EvidenceErrorCode.EVIDENCE_ACCESS_FORBIDDEN);
    }

    // ── 없는 것 · 지워진 것 ──────────────────────────────────

    @Test
    void refusesToServeDeletedEvidence() {
        EvidenceFileResponse uploaded = upload();
        deletionService.delete(uploaderId, uploaded.evidenceFileId());

        assertThatThrownBy(() -> contentService.read(uploaderId, uploaded.evidenceFileId()))
                .isInstanceOf(BusinessException.class)
                .extracting(EvidenceContentServiceTests::errorCodeOf)
                .isEqualTo(EvidenceErrorCode.EVIDENCE_NOT_FOUND);
    }

    @Test
    void reportsNotFoundForAnUnknownId() {
        assertThatThrownBy(() -> contentService.read(uploaderId, 999_999L))
                .isInstanceOf(BusinessException.class)
                .extracting(EvidenceContentServiceTests::errorCodeOf)
                .isEqualTo(EvidenceErrorCode.EVIDENCE_NOT_FOUND);
    }

    @Test
    void reportsFileNotFoundWhenTheRowSurvivesButTheObjectIsGone() throws Exception {
        // 행과 파일이 어긋난 상태. 500 으로 내보내면 프론트가 재시도 루프를 돌고
        // 우리는 장애 알림을 받는다 — 원인은 데이터 쪽인데.
        EvidenceFileResponse uploaded = upload();
        Files.delete(tempDir.resolve(uploaded.storageKey()));

        assertThatThrownBy(() -> contentService.read(uploaderId, uploaded.evidenceFileId()))
                .isInstanceOf(BusinessException.class)
                .extracting(EvidenceContentServiceTests::errorCodeOf)
                .isEqualTo(FileStorageErrorCode.FILE_NOT_FOUND);
    }
}
