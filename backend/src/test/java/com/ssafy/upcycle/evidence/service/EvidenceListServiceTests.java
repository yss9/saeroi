package com.ssafy.upcycle.evidence.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.common.config.ClockConfig;
import com.ssafy.upcycle.common.jpa.config.JpaAuditingConfig;
import com.ssafy.upcycle.material.service.MaterialEventRecorder;
import com.ssafy.upcycle.common.storage.FileStorageProperties;
import com.ssafy.upcycle.common.storage.FileStorageService;
import com.ssafy.upcycle.common.storage.LocalFileStorageService;
import com.ssafy.upcycle.evidence.dto.EvidenceListResponse;
import com.ssafy.upcycle.evidence.dto.EvidenceListVisibility;
import com.ssafy.upcycle.evidence.dto.EvidenceTypeCount;
import com.ssafy.upcycle.evidence.dto.EvidenceUploadRequest;
import com.ssafy.upcycle.evidence.entity.EvidenceReferenceType;
import com.ssafy.upcycle.evidence.entity.EvidenceType;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
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
 * 증빙 목록 인가. S15P21E104-286 §6-1.
 *
 * <p><b>여기서 고정하는 것은 "누가 무엇까지 보는가" 다.</b> 고치기 전 이 조회는 요청자를
 * 아예 받지 않아서, 로그인한 아무나 {@code referenceId} 만 바꿔 가며 남의 소재 증빙 목록을
 * 통째로 볼 수 있었다 — 파일명 · {@code contentPath} · {@code fileHash} · 업로더까지.
 * 파일 바이트는 {@code EvidenceAccessPolicy} 가 막았지만 목록은 인가를 거치지 않았다.
 *
 * <p>관계 판정을 {@code /content} 와 <b>같은 클래스</b>에서 가져오는 것도 여기서 확인한다.
 * 두 벌이 되면 {@code /content} 는 열리는데 목록은 막히는 상태가 조용히 생긴다 —
 * 그래서 이 클래스의 관계 설정({@code registrant} · {@code holder} · {@code stranger})은
 * {@code EvidenceContentServiceTests} 와 일부러 같은 모양이다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    JpaAuditingConfig.class,
    ClockConfig.class,
    EvidenceUploadService.class,
    MaterialEventRecorder.class,
    EvidenceReferenceValidator.class,
    EvidenceAccessPolicy.class,
    EvidenceListService.class,
    EvidenceListServiceTests.StorageTestConfig.class
})
class EvidenceListServiceTests {

    private static final byte[] JPEG = "photo-bytes".getBytes(StandardCharsets.UTF_8);
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
    private EvidenceListService listService;

    @Autowired
    private TestEntityManager entityManager;

    private Long registrantId;
    private Long uploaderId;
    private Long holderId;
    private Long strangerId;
    private Long materialId;

    @BeforeEach
    void setUp() {
        registrantId = persistUser("list-registrant@example.com", UserRole.SUPPLIER);
        uploaderId = persistUser("list-uploader@example.com", UserRole.PROCESSOR);
        holderId = persistUser("list-holder@example.com", UserRole.BRAND);
        strangerId = persistUser("list-stranger@example.com", UserRole.BRAND);

        materialId = entityManager
                .persistAndFlush(Material.register(
                        "MAT-000904",
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
                .persistAndFlush(User.register(email, "{noop}x", "사용자", "010-0000-0000", role))
                .getId();
    }

    private void upload(EvidenceType type, String fileName) {
        uploadService.upload(
                uploaderId,
                new EvidenceUploadRequest(EvidenceReferenceType.MATERIAL, materialId, type),
                List.of(new MockMultipartFile("files", fileName, "image/jpeg", JPEG)));
    }

    private EvidenceListResponse listedBy(Long requesterId) {
        return listService.findAll(requesterId, EvidenceReferenceType.MATERIAL, materialId);
    }

    // ── 전체를 보는 세 부류 ──────────────────────────────────

    @Test
    void theUploaderSeesTheWholeList() {
        upload(EvidenceType.WEIGHT_SLIP, "slip.jpg");

        EvidenceListResponse listed = listedBy(uploaderId);

        assertThat(listed.visibility()).isEqualTo(EvidenceListVisibility.FULL);
        assertThat(listed.files()).singleElement().satisfies(file -> {
            assertThat(file.storageKey()).startsWith("MATERIAL/" + materialId + "/");
            assertThat(file.fileHash()).isNotBlank();
            assertThat(file.uploadedBy()).isEqualTo(uploaderId);
        });
    }

    @Test
    void theRegistrantAndTheCurrentHolderSeeTheWholeList() {
        upload(EvidenceType.WEIGHT_SLIP, "slip.jpg");

        // /content 가 허용하는 것과 같은 세 부류다. 목록만 좁으면 프론트는 파일을 받을 수
        // 있는데 그 파일이 목록에 없는 상태를 만나게 된다.
        assertThat(listedBy(registrantId).visibility()).isEqualTo(EvidenceListVisibility.FULL);
        assertThat(listedBy(holderId).visibility()).isEqualTo(EvidenceListVisibility.FULL);
        assertThat(listedBy(holderId).files()).hasSize(1);
    }

    /** 목록 순서는 리포지토리가 고정한다 — "작업 전 → 작업 후" 처럼 시간 순서가 뜻을 갖는다. */
    @Test
    void listsEvidenceInUploadOrder() {
        upload(EvidenceType.BEFORE_PHOTO, "first.jpg");
        upload(EvidenceType.BEFORE_PHOTO, "second.jpg");

        var files = listedBy(uploaderId).files();

        assertThat(files).hasSize(2);
        assertThat(files.get(0).evidenceFileId()).isLessThan(files.get(1).evidenceFileId());
    }

    @Test
    void doesNotMixEvidenceOfAnotherReferenceType() {
        // reference_id 는 타입마다 따로 매겨진다. 같은 번호의 가공작업 증빙을
        // 소재의 것으로 읽으면 안 된다.
        upload(EvidenceType.BEFORE_PHOTO, "before.jpg");

        EvidenceListResponse listed = listService.findAll(
                uploaderId, EvidenceReferenceType.PROCESSING_JOB, materialId);

        assertThat(listed.files()).isNull();
        assertThat(listed.summary()).isEmpty();
    }

    // ── 관계 없는 인증 사용자 ────────────────────────────────

    /**
     * <b>이 테스트가 이 변경의 이유다.</b>
     *
     * <p>파일명({@code storageKey}) 하나로 거래 상대가 드러난다 —
     * {@code 계근표_OO상사.pdf}. 계근표에는 중량·단가가 찍혀 있다. 그래서 관계가 없는
     * 사용자의 응답에는 파일명 · {@code contentPath} · {@code fileHash} · 업로더가
     * <b>들어 있지 않아야 한다.</b>
     */
    @Test
    void hidesFileNamesHashesAndUploadersFromAnUnrelatedUser() {
        upload(EvidenceType.WEIGHT_SLIP, "slip.jpg");

        EvidenceListResponse listed = listedBy(strangerId);

        assertThat(listed.visibility()).isEqualTo(EvidenceListVisibility.SUMMARY);
        assertThat(listed.files())
                .as("빈 배열이 아니라 아예 없어야 한다 — 빈 배열은 '증빙이 없다' 는 뜻이고 그건 거짓이다")
                .isNull();
    }

    /**
     * 그래도 <b>개수는 보여준다.</b>
     *
     * <p>소재 상세는 추적성 화면이고 "계근표 1건, 작업 전 사진 2건 있음" 은 공개해도 되는
     * 값이다. 목록을 통째로 403 으로 막으면(선택지 A 단독) 그 섹션이 대부분 방문자에게
     * 빈칸이 된다.
     */
    @Test
    void stillReportsHowManyOfEachKindExist() {
        upload(EvidenceType.WEIGHT_SLIP, "slip.jpg");
        upload(EvidenceType.BEFORE_PHOTO, "a.jpg");
        upload(EvidenceType.BEFORE_PHOTO, "b.jpg");

        assertThat(listedBy(strangerId).summary())
                .containsExactly(
                        // 순서는 EvidenceType 열거 순서다 — 서버가 정한 순서가 화면 순서가 된다.
                        new EvidenceTypeCount(EvidenceType.BEFORE_PHOTO, 2),
                        new EvidenceTypeCount(EvidenceType.WEIGHT_SLIP, 1));
    }

    @Test
    void theSummaryIsAlsoPresentForSomeoneWhoSeesEverything() {
        // 전체를 보는 사람의 화면도 같은 요약 줄을 쓴다. 프론트가 files 를 세어 만들면
        // 그 계산이 두 곳에 생긴다.
        upload(EvidenceType.INVOICE, "invoice.jpg");

        assertThat(listedBy(registrantId).summary())
                .containsExactly(new EvidenceTypeCount(EvidenceType.INVOICE, 1));
    }

    // ── 확인할 수 없는 대상은 거절 쪽으로 기운다 ──────────────

    @Test
    void treatsAnUnknownTargetAsUnrelatedInsteadOfFailing() {
        // 404 로 답하면 그 응답이 소재 id 의 존재 여부를 알려주는 신호가 된다.
        // 목록을 묻는 요청이므로 빈 답이 정확하다.
        EvidenceListResponse listed =
                listService.findAll(registrantId, EvidenceReferenceType.MATERIAL, 999_999L);

        assertThat(listed.visibility()).isEqualTo(EvidenceListVisibility.SUMMARY);
        assertThat(listed.summary()).isEmpty();
    }

    @Test
    void countsOnlyEvidenceThatIsStillAlive() {
        // 지운 증빙이 개수에 남으면 "있다는데 안 보인다" 는 문의가 된다.
        upload(EvidenceType.WEIGHT_SLIP, "slip.jpg");
        entityManager
                .getEntityManager()
                .createQuery("update EvidenceFile e set e.deletedAt = CURRENT_TIMESTAMP")
                .executeUpdate();
        entityManager.clear();

        assertThat(listedBy(strangerId).summary()).isEmpty();
        assertThat(listedBy(registrantId).files())
                .as("등록자에게도 지워진 증빙은 보이지 않는다 — 목록은 비어 있고 응답은 전체 모양이다")
                .isEmpty();
    }
}
