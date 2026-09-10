package com.ssafy.upcycle.evidence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.upcycle.common.config.ClockConfig;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.common.jpa.config.JpaAuditingConfig;
import com.ssafy.upcycle.common.storage.FileStorageProperties;
import com.ssafy.upcycle.common.storage.FileStorageService;
import com.ssafy.upcycle.common.storage.LocalFileStorageService;
import com.ssafy.upcycle.evidence.EvidenceErrorCode;
import com.ssafy.upcycle.evidence.dto.EvidenceFileResponse;
import com.ssafy.upcycle.evidence.dto.EvidenceUploadRequest;
import com.ssafy.upcycle.evidence.entity.EvidenceReferenceType;
import com.ssafy.upcycle.evidence.entity.EvidenceType;
import com.ssafy.upcycle.evidence.repository.EvidenceFileRepository;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.service.MaterialEventRecorder;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
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
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 증빙 삭제 정책. S15P21E104-162.
 *
 * <p><b>여기서 고정하는 것은 "지워지지 않아야 하는 것이 지워지지 않는다" 다.</b>
 * 증빙 해시는 온체인 manifest 에 들어가고, 파일이 사라지면
 * {@code MaterialToken.verifyEvidence} 가 영구히 검증 불가가 된다. 복구 경로가 없다.
 *
 * <p>{@link EvidenceUploadServiceTests} 와 같은 방식으로 <b>실제 파일시스템</b>을 쓴다.
 * 확인해야 할 것 중 하나가 "정책이 막았을 때 바이트가 그대로 있는가" 이고, 스텁으로는
 * 그 사실을 알 수 없다.
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
    EvidenceDeletionTransaction.class,
    EvidenceListService.class,
    MaterialEventRecorder.class,
    EvidenceDeletionServiceTests.StorageTestConfig.class
})
class EvidenceDeletionServiceTests {

    private static final byte[] JPEG = "photo-bytes".getBytes(StandardCharsets.UTF_8);
    private static final String ANCHORED_HASH =
            "0x1111111111111111111111111111111111111111111111111111111111111111";

    static Path storageRoot;

    @TempDir
    static Path tempDir;

    @org.springframework.boot.test.context.TestConfiguration
    static class StorageTestConfig {
        @org.springframework.context.annotation.Bean
        FileStorageService fileStorageService() {
            storageRoot = tempDir;
            return new LocalFileStorageService(
                    FileStorageProperties.local(tempDir.toString()), Clock.systemDefaultZone());
        }
    }

    @Autowired
    private EvidenceUploadService uploadService;

    @Autowired
    private EvidenceDeletionService deletionService;

    @Autowired
    private EvidenceListService listService;

    @Autowired
    private EvidenceFileRepository evidenceFileRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Long uploaderId;
    private Long registrantId;
    private Long strangerId;
    private Long materialId;
    private Material material;

    @BeforeEach
    void setUp() throws Exception {
        // @DataJpaTest 는 DB 를 롤백하지만 디스크는 되돌리지 않는다. 컨텍스트가 클래스마다
        // 한 번 만들어져 저장 루트가 테스트 간에 공유되므로 여기서 비운다.
        try (Stream<Path> walk = Files.walk(tempDir)) {
            walk.filter(Files::isRegularFile).forEach(file -> {
                try {
                    Files.delete(file);
                } catch (Exception ignored) {
                    // 지우지 못하면 아래 단언이 실패해 드러난다.
                }
            });
        }

        // 업로더와 소재 등록자를 **다른 사람**으로 둔다. 둘을 같게 두면 "등록자도 지울 수
        // 있다" 와 "업로더가 지울 수 있다" 를 구분해 확인할 수 없다.
        registrantId = entityManager
                .persistAndFlush(User.register(
                        "del-registrant@example.com",
                        "{noop}x",
                        "등록자",
                        "010-0000-0000",
                        UserRole.SUPPLIER))
                .getId();
        uploaderId = entityManager
                .persistAndFlush(User.register(
                        "del-uploader@example.com",
                        "{noop}x",
                        "업로더",
                        "010-1111-1111",
                        UserRole.PROCESSOR))
                .getId();
        strangerId = entityManager
                .persistAndFlush(User.register(
                        "del-stranger@example.com",
                        "{noop}x",
                        "제3자",
                        "010-2222-2222",
                        UserRole.BRAND))
                .getId();

        material = entityManager.persistAndFlush(Material.register(
                "MAT-000901",
                MaterialType.DENIM,
                MaterialRootOrigin.WASTE,
                "폐데님 원단",
                null,
                "B",
                10_000L,
                null,
                null,
                registrantId));
        materialId = material.getId();
    }

    private EvidenceFileResponse upload() {
        return uploadService
                .upload(
                        uploaderId,
                        new EvidenceUploadRequest(
                                EvidenceReferenceType.MATERIAL,
                                materialId,
                                EvidenceType.BEFORE_PHOTO),
                        List.of(new MockMultipartFile("files", "before.jpg", "image/jpeg", JPEG)))
                .get(0);
    }

    /**
     * 온체인 고정 상태를 만든다.
     *
     * <p>필드를 직접 넣는다 — <b>발행·manifest 기록 경로가 아직 없다.</b> 그 경로가 없다고
     * 정책을 확인하지 않으면, 나중에 발행이 붙는 순간 증빙이 지워질 수 있는 상태로 배포된다.
     * 도메인 메서드를 미리 만들지 않은 것은 그 API 모양을 지금 지어내게 되기 때문이다.
     */
    private void anchorWithManifest() {
        ReflectionTestUtils.setField(material, "evidenceHash", ANCHORED_HASH);
        entityManager.persistAndFlush(material);
    }

    private void anchorWithToken() {
        ReflectionTestUtils.setField(material, "tokenId", 42L);
        entityManager.persistAndFlush(material);
    }

    private long filesOnDisk() throws Exception {
        try (Stream<Path> walk = Files.walk(storageRoot)) {
            return walk.filter(Files::isRegularFile).count();
        }
    }

    private static com.ssafy.upcycle.common.exception.ErrorCode errorCodeOf(Throwable failure) {
        return ((BusinessException) failure).getErrorCode();
    }

    // ── 정상 경로 ────────────────────────────────────────────

    @Test
    void softDeletesTheRowAndRemovesTheBytes() throws Exception {
        EvidenceFileResponse uploaded = upload();

        EvidenceFileResponse deleted = deletionService.delete(uploaderId, uploaded.evidenceFileId());

        assertThat(deleted.deletedAt()).as("삭제 시각이 응답에 있어야 반영을 확인할 수 있다").isNotNull();
        assertThat(filesOnDisk()).as("저장소의 바이트는 실제로 사라진다").isZero();
        assertThat(evidenceFileRepository.findById(uploaded.evidenceFileId()))
                .as("행은 남는다 — file_hash 가 온체인 manifest 에 들어갈 수 있다")
                .isPresent()
                .get()
                .satisfies(row -> {
                    assertThat(row.isDeleted()).isTrue();
                    assertThat(row.getDeletedBy()).isEqualTo(uploaderId);
                    assertThat(row.getFileHash()).hasSize(66);
                });
    }

    @Test
    void theMaterialRegistrantCanDeleteEvidenceSomeoneElseUploaded() {
        EvidenceFileResponse uploaded = upload();

        assertThat(deletionService.delete(registrantId, uploaded.evidenceFileId()).deletedAt())
                .isNotNull();
    }

    @Test
    void deletedEvidenceDisappearsFromTheList() {
        EvidenceFileResponse uploaded = upload();
        deletionService.delete(uploaderId, uploaded.evidenceFileId());

        assertThat(listService
                        .findAll(registrantId, EvidenceReferenceType.MATERIAL, materialId)
                        .files())
                .as("지워진 증빙이 목록에 남으면 화면이 깨진 이미지를 그린다")
                .isEmpty();
    }

    // ── 온체인 고정은 아무도 지울 수 없다 ────────────────────

    @Test
    void refusesToDeleteWhenTheManifestHashIsRecorded() throws Exception {
        EvidenceFileResponse uploaded = upload();
        anchorWithManifest();

        assertThatThrownBy(() -> deletionService.delete(uploaderId, uploaded.evidenceFileId()))
                .isInstanceOf(BusinessException.class)
                .extracting(EvidenceDeletionServiceTests::errorCodeOf)
                .isEqualTo(EvidenceErrorCode.EVIDENCE_ANCHORED_ON_CHAIN);

        assertThat(filesOnDisk()).as("막았으면 바이트가 그대로 있어야 한다").isEqualTo(1);
        assertThat(evidenceFileRepository.findById(uploaded.evidenceFileId()))
                .get()
                .satisfies(row -> assertThat(row.isDeleted()).isFalse());
    }

    @Test
    void refusesToDeleteWhenTheTokenWasMinted() throws Exception {
        // manifest 컬럼이 비어 있어도 발행 트랜잭션에 증빙이 함께 들어갈 수 있다.
        // 모호할 때 막는 쪽으로 기운다 — 잘못 지우면 복구가 없다.
        EvidenceFileResponse uploaded = upload();
        anchorWithToken();

        assertThatThrownBy(() -> deletionService.delete(uploaderId, uploaded.evidenceFileId()))
                .isInstanceOf(BusinessException.class)
                .extracting(EvidenceDeletionServiceTests::errorCodeOf)
                .isEqualTo(EvidenceErrorCode.EVIDENCE_ANCHORED_ON_CHAIN);
        assertThat(filesOnDisk()).isEqualTo(1);
    }

    @Test
    void theRegistrantCannotDeleteAnchoredEvidenceEither() {
        // 권한이 아니라 상태의 문제다. 권한이 있는 사람에게도 같은 답이 나가야 한다.
        EvidenceFileResponse uploaded = upload();
        anchorWithManifest();

        assertThatThrownBy(() -> deletionService.delete(registrantId, uploaded.evidenceFileId()))
                .isInstanceOf(BusinessException.class)
                .extracting(EvidenceDeletionServiceTests::errorCodeOf)
                .isEqualTo(EvidenceErrorCode.EVIDENCE_ANCHORED_ON_CHAIN);
    }

    // ── 인가 ────────────────────────────────────────────────

    @Test
    void refusesAThirdPartyEvenBeforeLookingAtTheChain() throws Exception {
        EvidenceFileResponse uploaded = upload();
        anchorWithManifest();

        // 온체인 고정 상태에서도 **403** 이 나가야 한다. 409 가 나가면 권한 없는 요청자가
        // 응답 코드만으로 "그 소재는 체인에 올라갔다" 를 알아낸다.
        assertThatThrownBy(() -> deletionService.delete(strangerId, uploaded.evidenceFileId()))
                .isInstanceOf(BusinessException.class)
                .extracting(EvidenceDeletionServiceTests::errorCodeOf)
                .isEqualTo(EvidenceErrorCode.EVIDENCE_DELETE_FORBIDDEN);

        assertThat(filesOnDisk()).isEqualTo(1);
    }

    // ── 두 번 지우지 못한다 ──────────────────────────────────

    @Test
    void deletingTwiceReportsNotFound() {
        // 두 번째 삭제가 통과하면 deleted_by 가 나중 사람으로 덮여 처음 지운 사람이
        // 감사 기록에서 사라진다. 없는 것과 지워진 것을 같은 답으로 묶는 이유는
        // "그 id 의 증빙은 존재했다" 를 새어 나가게 하지 않기 위해서다.
        EvidenceFileResponse uploaded = upload();
        deletionService.delete(uploaderId, uploaded.evidenceFileId());

        assertThatThrownBy(() -> deletionService.delete(uploaderId, uploaded.evidenceFileId()))
                .isInstanceOf(BusinessException.class)
                .extracting(EvidenceDeletionServiceTests::errorCodeOf)
                .isEqualTo(EvidenceErrorCode.EVIDENCE_NOT_FOUND);
    }

    @Test
    void reportsNotFoundForAnUnknownId() {
        assertThatThrownBy(() -> deletionService.delete(uploaderId, 999_999L))
                .isInstanceOf(BusinessException.class)
                .extracting(EvidenceDeletionServiceTests::errorCodeOf)
                .isEqualTo(EvidenceErrorCode.EVIDENCE_NOT_FOUND);
    }
}
