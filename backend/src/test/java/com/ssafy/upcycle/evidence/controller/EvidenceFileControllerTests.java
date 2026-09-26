package com.ssafy.upcycle.evidence.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.evidence.dto.EvidenceFileResponse;
import com.ssafy.upcycle.evidence.dto.EvidenceUploadRequest;
import com.ssafy.upcycle.evidence.entity.EvidenceReferenceType;
import com.ssafy.upcycle.evidence.entity.EvidenceType;
import com.ssafy.upcycle.evidence.repository.EvidenceFileRepository;
import com.ssafy.upcycle.evidence.service.EvidenceUploadService;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 증빙 파일 조회·삭제의 <b>HTTP 표면</b>. S15P21E104-162.
 *
 * <p>정책 자체는 {@code EvidenceContentServiceTests} · {@code EvidenceDeletionServiceTests} 가
 * 확인한다. 여기서 고정하는 것은 그것이 <b>어떤 응답으로 나가는가</b>다.
 *
 * <ul>
 *   <li>{@code /content} 는 <b>봉투가 아니라 바이트</b>다 — base64 로 감싸면 크기가 33%
 *       늘고 {@code <img src>} 로 쓸 수 없다
 *   <li>실패는 다른 API 와 <b>같은 봉투</b>다 — 프론트가 여기만 다르게 분기하지 않아도 된다
 *   <li>토큰이 없으면 컨트롤러에 닿기 전에 401 이다 ({@code SecurityConfig} 를 고치지 않았다
 *       는 사실을 이 테스트가 고정한다)
 *   <li><b>목록은 보는 사람에 따라 모양이 다르다</b> (S15P21E104-286 §6-1) — 관계가 없는
 *       인증 사용자의 응답에는 파일명·해시·업로더가 <b>본문 어디에도</b> 없다.
 *       판정 자체는 {@code EvidenceListServiceTests} 가 확인한다
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class EvidenceFileControllerTests {

    private static final byte[] JPEG = "photo-bytes".getBytes(StandardCharsets.UTF_8);
    private static final String ANCHORED_HASH =
            "0x2222222222222222222222222222222222222222222222222222222222222222";

    /**
     * 저장 루트를 임시 디렉터리로 돌린다.
     *
     * <p>{@code @SpringBootTest} 는 실제 {@code FileStorageService} 빈을 쓰고 기본 루트가
     * {@code /app/files} 다 — 로컬에서 돌리면 <b>레포 밖 실제 경로에 파일을 쓴다.</b>
     * 컨텍스트 생성 전에 값이 필요하므로 {@code @DynamicPropertySource} 로 넣는다.
     */
    private static final Path STORAGE_ROOT = createStorageRoot();

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("evidence-controller-");
        } catch (IOException e) {
            throw new IllegalStateException("임시 저장 루트를 만들 수 없다", e);
        }
    }

    @DynamicPropertySource
    static void storageRoot(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", STORAGE_ROOT::toString);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private EvidenceUploadService uploadService;

    @Autowired
    private EvidenceFileRepository evidenceFileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private MaterialBalanceRepository materialBalanceRepository;

    private String uploaderToken;
    private String strangerToken;
    private Long uploaderId;
    private Long materialId;

    @BeforeEach
    void setUp() {
        clean();
        User registrant = userRepository.save(User.register(
                "ctl-registrant@example.com", "{noop}x", "등록자", "010-0000-0000", UserRole.SUPPLIER));
        User uploader = userRepository.save(User.register(
                "ctl-uploader@example.com", "{noop}x", "업로더", "010-1111-1111", UserRole.PROCESSOR));
        User stranger = userRepository.save(User.register(
                "ctl-stranger@example.com", "{noop}x", "제3자", "010-2222-2222", UserRole.BRAND));
        uploaderId = uploader.getId();
        uploaderToken = tokenFor(uploader);
        strangerToken = tokenFor(stranger);

        materialId = materialRepository
                .save(Material.register(
                        "MAT-000903",
                        MaterialType.DENIM,
                        MaterialRootOrigin.WASTE,
                        "폐데님 원단",
                        null,
                        "B",
                        10_000L,
                        null,
                        null,
                        registrant.getId()))
                .getId();
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    private Long uploadEvidence() {
        return uploadEvidence(EvidenceType.BEFORE_PHOTO);
    }

    /** 종류를 고를 수 있는 버전 — 사진류/문서류 접근 판정이 종류에 따라 갈리는 테스트에 쓴다. */
    private Long uploadEvidence(EvidenceType evidenceType) {
        EvidenceFileResponse uploaded = uploadService
                .upload(
                        uploaderId,
                        new EvidenceUploadRequest(
                                EvidenceReferenceType.MATERIAL,
                                materialId,
                                evidenceType),
                        List.of(new MockMultipartFile("files", "before.jpg", "image/jpeg", JPEG)))
                .get(0);
        return uploaded.evidenceFileId();
    }

    // ── 조회 ────────────────────────────────────────────────

    @Test
    void servesTheBytesWithTheDerivedMediaTypeAndNoEnvelope() throws Exception {
        Long evidenceFileId = uploadEvidence();

        mockMvc.perform(get("/api/evidence/" + evidenceFileId + "/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + uploaderToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(JPEG))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(JPEG.length)))
                // 스니핑을 막지 않으면 화이트리스트로 svg 를 거른 노력이 무의미해진다.
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                // 증빙은 인가가 걸린 자료다. 공용 프록시가 캐시하면 남에게 그대로 나간다.
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, max-age=0, no-store"))
                // 원본 파일명을 헤더에 싣지 않는다 — 사용자가 정한 값이고 개행·따옴표가 들어가면
                // 헤더 인젝션이 된다.
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline"));
    }

    @Test
    void contentRequiresAuthentication() throws Exception {
        Long evidenceFileId = uploadEvidence();

        mockMvc.perform(get("/api/evidence/" + evidenceFileId + "/content"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 문서류(계근표·거래명세서)는 여전히 관계가 있어야 한다 — 거래 상대·중량·단가가
     * 찍혀 있어서다.
     */
    @Test
    void refusesAThirdPartyWithTheSharedEnvelope() throws Exception {
        Long evidenceFileId = uploadEvidence(EvidenceType.WEIGHT_SLIP);

        mockMvc.perform(get("/api/evidence/" + evidenceFileId + "/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("EVIDENCE_ACCESS_FORBIDDEN"));
    }

    /**
     * 사진류는 관계 없는 제3자에게도 열려 있다 — 마켓플레이스를 구경하는 구매자가
     * 사진 없이 사라고 할 수는 없다 (`EvidenceAccessPolicy` 의 사진류 예외).
     */
    @Test
    void allowsAThirdPartyToReadAPublicPhoto() throws Exception {
        Long evidenceFileId = uploadEvidence(EvidenceType.BEFORE_PHOTO);

        mockMvc.perform(get("/api/evidence/" + evidenceFileId + "/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
                .andExpect(status().isOk());
    }

    @Test
    void reportsNotFoundForAnUnknownEvidenceId() throws Exception {
        mockMvc.perform(get("/api/evidence/999999/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + uploaderToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVIDENCE_NOT_FOUND"));
    }

    @Test
    void theUploadResponseCarriesTheContentPath() throws Exception {
        Long evidenceFileId = uploadEvidence();

        mockMvc.perform(list(uploaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibility").value("FULL"))
                .andExpect(jsonPath("$.data.files[0].contentPath")
                        .value("/api/evidence/" + evidenceFileId + "/content"))
                .andExpect(jsonPath("$.data.files[0].deletedAt").doesNotExist());
    }

    // ── 목록 인가 (S15P21E104-286 §6-1) ──────────────────────

    /**
     * <b>관계 없는 인증 사용자의 응답에 민감한 값이 하나도 들어 있지 않다.</b>
     *
     * <p>고치기 전 이 엔드포인트는 JWT 를 받지 않았고, 로그인한 아무나 {@code referenceId} 만
     * 바꿔 가며 남의 소재 증빙 목록을 통째로 봤다. 파일 바이트는
     * {@code EvidenceAccessPolicy} 가 막았지만 {@code 계근표_OO상사.pdf} 라는 이름 하나로
     * 거래 상대가 드러나고, 계근표에는 중량·단가가 찍혀 있다.
     *
     * <p><b>필드 이름이 아니라 응답 본문 전체를 본다.</b> {@code jsonPath} 로 알려진 필드만
     * 확인하면 <b>나중에 추가되는 필드가 조용히 새어 나가고</b>, 응답 모양을 바꾸면 단언이
     * 지나가 버린다. 여기서는 "이 값들이 본문 어디에도 없다" 를 못 박는다.
     */
    @Test
    void doesNotLeakFileNamesHashesOrUploadersToAnUnrelatedUser() throws Exception {
        Long evidenceFileId = uploadEvidence();
        var stored = evidenceFileRepository.findByIdAndDeletedAtIsNull(evidenceFileId).orElseThrow();

        String body = mockMvc.perform(list(strangerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(body)
                .as("파일명(storageKey) 하나로 거래 상대가 드러난다")
                .doesNotContain(stored.getFileUrl())
                .as("fileHash 는 온체인 대조용이고 관계 있는 사람에게만 뜻이 있다")
                .doesNotContain(stored.getFileHash())
                .as("contentPath 는 증빙 id 를 그대로 노출한다")
                .doesNotContain("/content")
                .as("업로더 id 는 누가 이 소재를 다루는지 알려준다")
                .doesNotContain("uploadedBy");
    }

    /**
     * 그래도 <b>개수는 보여준다.</b> 소재 상세는 추적성 화면이라 "계근표 1건 있음" 은
     * 공개 가치가 있다. 목록을 통째로 403 으로 막으면 그 섹션이 대부분 방문자에게 빈칸이 된다.
     */
    @Test
    void showsOnlyTheCountsByEvidenceTypeToAnUnrelatedUser() throws Exception {
        uploadEvidence();

        mockMvc.perform(list(strangerToken))
                .andExpect(status().isOk())
                // 응답에 "가려졌다" 는 사실이 실려 나간다 — 필드를 조용히 빼면 프론트가
                // "값이 없는 것" 과 "가려진 것" 을 구분할 수 없다.
                .andExpect(jsonPath("$.data.visibility").value("SUMMARY"))
                .andExpect(jsonPath("$.data.files").doesNotExist())
                .andExpect(jsonPath("$.data.summary[0].evidenceType").value("BEFORE_PHOTO"))
                .andExpect(jsonPath("$.data.summary[0].count").value(1));
    }

    /**
     * 인증 요구는 그대로다.
     *
     * <p>비회원에게 요약을 열지는 Matrix §6-3 · §6-4 (기획 결정)에 걸려 있고 <b>미결</b>이다.
     * 이번 변경은 인증된 사용자 사이의 구분만 한다 — 그 사실을 이 테스트가 고정한다.
     */
    @Test
    void theListStillRequiresAuthentication() throws Exception {
        uploadEvidence();

        mockMvc.perform(get("/api/evidence")
                        .param("referenceType", "MATERIAL")
                        .param("referenceId", String.valueOf(materialId)))
                .andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder list(String token) {
        return get("/api/evidence")
                .param("referenceType", "MATERIAL")
                .param("referenceId", String.valueOf(materialId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    // ── 삭제 ────────────────────────────────────────────────

    @Test
    void deletesAndReportsTheDeletionTimestamp() throws Exception {
        Long evidenceFileId = uploadEvidence();

        mockMvc.perform(delete("/api/evidence/" + evidenceFileId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + uploaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.evidenceFileId").value(evidenceFileId))
                // 204 가 아니라 이 값으로 반영을 확인한다.
                .andExpect(jsonPath("$.data.deletedAt").exists());

        assertThat(evidenceFileRepository.findByIdAndDeletedAtIsNull(evidenceFileId))
                .as("소프트 삭제된 행은 조회 경로에서 빠진다")
                .isEmpty();
        assertThat(evidenceFileRepository.findById(evidenceFileId))
                .as("행 자체는 남는다 — file_hash 가 온체인 manifest 에 들어갈 수 있다")
                .isPresent();
    }

    @Test
    void deleteRequiresAuthentication() throws Exception {
        Long evidenceFileId = uploadEvidence();

        mockMvc.perform(delete("/api/evidence/" + evidenceFileId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refusesToDeleteAnchoredEvidenceWithConflict() throws Exception {
        Long evidenceFileId = uploadEvidence();
        anchorMaterial();

        mockMvc.perform(delete("/api/evidence/" + evidenceFileId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + uploaderToken))
                // 409 다 — 요청이 틀린 것도 권한 문제도 아니고, 되돌아가지 않는 상태다.
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVIDENCE_ANCHORED_ON_CHAIN"));

        assertThat(evidenceFileRepository.findByIdAndDeletedAtIsNull(evidenceFileId)).isPresent();
    }

    @Test
    void refusesADeleteFromAThirdParty() throws Exception {
        Long evidenceFileId = uploadEvidence();

        mockMvc.perform(delete("/api/evidence/" + evidenceFileId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EVIDENCE_DELETE_FORBIDDEN"));
    }

    /**
     * 온체인 고정 상태를 만든다. 발행·manifest 기록 경로가 아직 없어 필드를 직접 넣는다 —
     * 이유는 {@code EvidenceDeletionServiceTests} 주석에 있다.
     */
    private void anchorMaterial() {
        Material material = materialRepository.findById(materialId).orElseThrow();
        ReflectionTestUtils.setField(material, "evidenceHash", ANCHORED_HASH);
        materialRepository.saveAndFlush(material);
    }

    private String tokenFor(User user) {
        return jwtTokenService
                .issueAccessToken(String.valueOf(user.getId()), List.of(user.getRole().name()))
                .value();
    }

    private void clean() {
        evidenceFileRepository.deleteAll();
        materialBalanceRepository.deleteAll();
        materialRepository.deleteAll();
        userRepository.deleteAll();
    }
}
