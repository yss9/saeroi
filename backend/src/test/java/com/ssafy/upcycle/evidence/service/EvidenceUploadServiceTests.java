package com.ssafy.upcycle.evidence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.common.config.ClockConfig;
import com.ssafy.upcycle.common.jpa.config.JpaAuditingConfig;
import com.ssafy.upcycle.common.storage.FileStorageErrorCode;
import com.ssafy.upcycle.common.storage.FileStorageProperties;
import com.ssafy.upcycle.common.storage.FileStorageService;
import com.ssafy.upcycle.common.storage.LocalFileStorageService;
import com.ssafy.upcycle.evidence.EvidenceErrorCode;
import com.ssafy.upcycle.evidence.dto.EvidenceUploadRequest;
import com.ssafy.upcycle.evidence.entity.EvidenceReferenceType;
import com.ssafy.upcycle.evidence.entity.EvidenceType;
import com.ssafy.upcycle.evidence.repository.EvidenceFileRepository;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.service.MaterialEventRecorder;
import com.ssafy.upcycle.material.entity.MaterialEventType;
import com.ssafy.upcycle.material.repository.MaterialEventRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.stream.IntStream;
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
 * 증빙 업로드. S15P21E104-249 · S15P21E104-252.
 *
 * <p><b>여기서 확인하는 것은 실패 경로다.</b> 증빙 파일의 해시는 온체인에 올라가므로
 * 잘못 남은 것을 되돌릴 방법이 없다. 그래서 다음 셋을 못 박는다.
 *
 * <ol>
 *   <li>없는 대상에 증빙이 붙지 않는다 ({@code reference_id} 에 FK 가 없어 DB 가 막지 못한다)</li>
 *   <li>실패했을 때 파일이 디스크에 남지 않는다</li>
 *   <li>DB 행과 실제 파일이 어긋나지 않는다</li>
 * </ol>
 *
 * <p>저장소를 스텁으로 바꾸지 않고 <b>실제 파일시스템</b>({@code @TempDir})을 쓴다.
 * 확인해야 할 것 중 하나가 "실패 후 디스크 상태" 이고, 그건 스텁으로는 알 수 없다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    JpaAuditingConfig.class,
    ClockConfig.class,
    EvidenceUploadService.class,
    EvidenceReferenceValidator.class,
    MaterialEventRecorder.class,
    EvidenceUploadServiceTests.StorageTestConfig.class
})
class EvidenceUploadServiceTests {

    private static final byte[] JPEG = "photo-bytes".getBytes(StandardCharsets.UTF_8);

    /**
     * 저장 루트를 테스트마다 새로 만든다.
     *
     * <p>{@code @TempDir} 은 인스턴스 필드라 스프링 컨텍스트 생성 시점에는 없다.
     * 그래서 정적 경로를 미리 잡아 두고 빈이 그것을 쓰게 한다 — 컨텍스트가 테스트
     * 클래스마다 한 번 만들어지므로 클래스 안에서는 같은 루트를 공유한다.
     */
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
    private EvidenceFileRepository evidenceFileRepository;

    @Autowired
    private MaterialEventRepository materialEventRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Long uploaderId;
    private Long materialId;

    @BeforeEach
    void setUp() throws Exception {
        // @DataJpaTest 는 DB 를 롤백하지만 **디스크는 되돌리지 않는다.** 스프링 컨텍스트가
        // 클래스마다 한 번 만들어져 저장 루트가 테스트 간에 공유되므로, 앞 테스트가 쓴
        // 파일이 남아 "실패했는데 파일이 없다" 를 확인하는 단언이 거짓으로 통과하거나
        // 개수 단언이 어긋난다. 여기서 비운다.
        try (Stream<Path> walk = Files.walk(tempDir)) {
            walk.filter(Files::isRegularFile).forEach(file -> {
                try {
                    Files.delete(file);
                } catch (Exception ignored) {
                    // 지우지 못하면 아래 단언이 실패해 드러난다. 여기서 감추지 않는다.
                }
            });
        }

        User uploader = entityManager.persistAndFlush(User.register(
                "supplier@example.com", "{noop}x", "공급자", "010-0000-0000", UserRole.SUPPLIER));
        uploaderId = uploader.getId();
        materialId = entityManager
                .persistAndFlush(Material.register(
                        "MAT-000801",
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
    }

    private EvidenceUploadRequest request() {
        return new EvidenceUploadRequest(
                EvidenceReferenceType.MATERIAL, materialId, EvidenceType.BEFORE_PHOTO);
    }

    private static MockMultipartFile jpeg(String name) {
        return new MockMultipartFile("files", name, "image/jpeg", JPEG);
    }

    private long filesOnDisk() throws Exception {
        try (Stream<Path> walk = Files.walk(storageRoot)) {
            return walk.filter(Files::isRegularFile).count();
        }
    }

    // ── 정상 경로 ────────────────────────────────────────────

    @Test
    void storesTheFileAndRecordsIt() throws Exception {
        var uploaded = uploadService.upload(uploaderId, request(), List.of(jpeg("before.jpg")));

        assertThat(uploaded).singleElement().satisfies(response -> {
            assertThat(response.storageKey()).startsWith("MATERIAL/" + materialId + "/");
            assertThat(response.fileHash()).hasSize(66).startsWith("0x");
            assertThat(response.uploadedBy()).isEqualTo(uploaderId);
            assertThat(response.uploadedAt()).isNotNull();
        });
        assertThat(filesOnDisk()).isEqualTo(1);
        assertThat(materialEventRepository.findAll())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getEventType()).isEqualTo(MaterialEventType.EVIDENCE_ATTACHED);
                    assertThat(event.getMaterialId()).isEqualTo(materialId);
                    assertThat(event.getReferenceId()).isEqualTo(uploaded.getFirst().evidenceFileId());
                });
    }

    @Test
    void theRecordedKeyPointsAtTheFileThatWasWritten() throws Exception {
        // DB 행과 실제 파일이 어긋나면 증빙을 되찾을 수 없고, 그 해시는 이미 온체인에
        // 올라가 있을 수 있다.
        var uploaded = uploadService.upload(uploaderId, request(), List.of(jpeg("before.jpg")));

        Path written = storageRoot.resolve(uploaded.get(0).storageKey());
        assertThat(Files.readAllBytes(written)).isEqualTo(JPEG);
    }

    @Test
    void acceptsSeveralFilesInOneRequest() throws Exception {
        uploadService.upload(
                uploaderId, request(), List.of(jpeg("a.jpg"), jpeg("b.jpg"), jpeg("c.jpg")));

        assertThat(evidenceFileRepository.findAll()).hasSize(3);
        assertThat(filesOnDisk()).isEqualTo(3);
    }

    // 목록 조회 테스트는 EvidenceListServiceTests 로 옮겼다 — 조회가 인가를 갖게 되면서
    // EvidenceListService 로 이동했다 (S15P21E104-286 §6-1).

    // ── 대상 확인 ────────────────────────────────────────────

    @Test
    void rejectsAReferenceThatDoesNotExist() throws Exception {
        // evidence_file.reference_id 에는 FK 가 없다. 여기서 막지 않으면 없는 소재를
        // 가리키는 증빙이 남고, 그 파일의 해시는 온체인에 올라갈 수 있다.
        var missing = new EvidenceUploadRequest(
                EvidenceReferenceType.MATERIAL, materialId + 999, EvidenceType.BEFORE_PHOTO);

        assertThatThrownBy(() -> uploadService.upload(uploaderId, missing, List.of(jpeg("a.jpg"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(EvidenceErrorCode.EVIDENCE_REFERENCE_NOT_FOUND);

        assertThat(filesOnDisk()).as("검증 전에는 한 바이트도 쓰지 않는다").isZero();
    }

    @Test
    void rejectsReferenceTypesThatCannotBeCheckedYet() throws Exception {
        // processing·production 은 엔티티가 없어 존재를 확인할 방법이 없다.
        // "없는 대상" 과 다른 코드를 주어야 사용자가 맞는 요청을 계속 고치지 않는다.
        var unsupported = new EvidenceUploadRequest(
                EvidenceReferenceType.PROCESSING_JOB, 1L, EvidenceType.PROCESS_PHOTO);

        assertThatThrownBy(() ->
                        uploadService.upload(uploaderId, unsupported, List.of(jpeg("a.jpg"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(EvidenceErrorCode.EVIDENCE_REFERENCE_NOT_SUPPORTED);

        assertThat(filesOnDisk()).isZero();
    }

    @Test
    void rejectsAnUnknownUploader() throws Exception {
        assertThatThrownBy(() ->
                        uploadService.upload(uploaderId + 999, request(), List.of(jpeg("a.jpg"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(EvidenceErrorCode.EVIDENCE_UPLOADER_NOT_FOUND);

        assertThat(filesOnDisk()).isZero();
    }

    // ── 요청 단위 상한 ───────────────────────────────────────

    @Test
    void rejectsAnEmptyFileList() throws Exception {
        assertThatThrownBy(() -> uploadService.upload(uploaderId, request(), List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(EvidenceErrorCode.EVIDENCE_FILE_MISSING);

        assertThat(filesOnDisk()).isZero();
    }

    @Test
    void acceptsExactlyTenFiles() throws Exception {
        List<MockMultipartFile> ten =
                IntStream.range(0, 10).mapToObj(i -> jpeg("photo" + i + ".jpg")).toList();

        uploadService.upload(uploaderId, request(), List.copyOf(ten));

        assertThat(filesOnDisk()).isEqualTo(10);
    }

    @Test
    void rejectsMoreThanTenFilesWithoutWritingAny() throws Exception {
        // 쓰다가 열한 번째에서 발견하면 이미 쓴 열 개를 지워야 하고, 그 지우기가
        // 실패하면 고아 파일이 남는다. 쓰기 전에 걸러내면 그 경로 자체가 없다.
        List<MockMultipartFile> eleven =
                IntStream.range(0, 11).mapToObj(i -> jpeg("photo" + i + ".jpg")).toList();

        assertThatThrownBy(() -> uploadService.upload(uploaderId, request(), List.copyOf(eleven)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(FileStorageErrorCode.FILE_COUNT_EXCEEDED);

        assertThat(filesOnDisk()).isZero();
        assertThat(evidenceFileRepository.findAll()).isEmpty();
    }

    @Test
    void rejectsAFileLargerThanTenMegabytes() throws Exception {
        MockMultipartFile huge = new MockMultipartFile(
                "files", "huge.jpg", "image/jpeg", new byte[10 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> uploadService.upload(uploaderId, request(), List.of(huge)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(FileStorageErrorCode.FILE_TOO_LARGE);

        assertThat(filesOnDisk()).isZero();
    }

    @Test
    void rejectsAnEmptyFile() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("files", "empty.jpg", "image/jpeg", new byte[0]);

        assertThatThrownBy(() -> uploadService.upload(uploaderId, request(), List.of(empty)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(FileStorageErrorCode.FILE_EMPTY);

        assertThat(filesOnDisk()).isZero();
    }

    // ── 부분 실패 정리 ───────────────────────────────────────

    @Test
    void leavesNoFileBehindWhenALaterFileIsRejected() throws Exception {
        // 첫 파일은 정상이고 두 번째가 svg 다 (사용자에게는 이미지, 브라우저에는
        // 스크립트 컨테이너). 첫 파일이 이미 디스크에 있는 상태에서 실패한다.
        MockMultipartFile good = jpeg("before.jpg");
        MockMultipartFile hostile =
                new MockMultipartFile("files", "logo.svg", "image/svg+xml", JPEG);

        assertThatThrownBy(() ->
                        uploadService.upload(uploaderId, request(), List.of(good, hostile)))
                .isInstanceOf(BusinessException.class);

        assertThat(filesOnDisk())
                .as("먼저 쓴 파일도 함께 지워야 한다 — 아무도 가리키지 않는 고아가 된다")
                .isZero();
        assertThat(evidenceFileRepository.findAll()).isEmpty();
    }
}
