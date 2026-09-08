package com.ssafy.upcycle.evidence.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.evidence.repository.EvidenceFileRepository;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.util.unit.DataSize;

/**
 * 증빙 업로드의 <b>HTTP 표면</b>. S15P21E104-255.
 *
 * <p>정책 판정 자체는 {@code FileContentPolicyTests}(파일 하나) ·
 * {@code EvidenceUploadServiceTests}(요청 단위) 가 이미 확인한다. <b>여기서 고정하는 것은
 * 그 판정이 multipart 요청을 거쳐 어떤 응답으로 나가는가</b>다. 아래 셋은 서비스 단위
 * 테스트가 전부 통과해도 따로 깨질 수 있다.
 *
 * <ol>
 *   <li>{@code @ModelAttribute} 로 오는 폼 필드({@code referenceType} · {@code referenceId} ·
 *       {@code evidenceType})가 실제로 바인딩되는가 — 이름이 하나만 틀려도 컴파일은 되고
 *       테스트도 각각 통과하는데 프론트만 400 을 받는다
 *   <li>{@code @RequestParam("files")} 파트 이름이 프론트와의 계약이다
 *   <li>{@code FileStorageErrorCode} 가 정책 문서의 Error Contract 그대로 나가는가 —
 *       프론트는 {@code code} 문자열로 분기한다
 * </ol>
 *
 * <p>토큰 없으면 컨트롤러에 닿기 전에 401 이다. {@code SecurityConfig} 의 permitAll 목록에
 * 증빙 경로가 없다는 사실을 이 테스트가 고정한다 — 증빙은 온체인에 해시가 남는 기록이라
 * 익명 업로드가 열리면 되돌릴 수 없다.
 *
 * <h2>크기 상한 3층 중 이 클래스가 닿는 층</h2>
 *
 * <p>MockMvc 는 <b>실제 multipart 파싱을 하지 않는다</b> —
 * {@code MockMultipartHttpServletRequest} 를 직접 만들어 넘기므로
 * {@code spring.servlet.multipart.max-file-size} 는 여기서 작동하지 않는다. 그래서 10MB 를
 * 넘는 파일이 컨테이너 층을 그대로 통과해 {@code EvidenceUploadService} 까지 도달하고,
 * 거기서 {@code FILE_TOO_LARGE} 가 된다. <b>이것이 2층 단독 검증이다</b> — 1층이 꺼져 있어도
 * 애플리케이션이 스스로 막는다는 뜻이고, 정책 문서가 "두 층에서 막는 것은 중복이 아니다"
 * 라고 쓴 이유가 이것이다. 1층(설정 값과 예외 번역)은
 * {@code com.ssafy.upcycle.common.storage.FileSizeLimitLayerTests} 가 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EvidenceUploadControllerTests {

    private static final byte[] JPEG = "photo-bytes".getBytes(StandardCharsets.UTF_8);
    private static final long TEN_MEGABYTES = 10L * 1024 * 1024;

    /**
     * 저장 루트를 임시 디렉터리로 돌린다. 기본값이 {@code /app/files} 라 그대로 두면
     * 로컬에서 <b>레포 밖 실제 경로에 파일을 쓴다.</b> 컨텍스트 생성 전에 값이 필요하므로
     * {@code @DynamicPropertySource} 로 넣는다 ({@code EvidenceFileControllerTests} 와 같다).
     */
    private static final Path STORAGE_ROOT = createStorageRoot();

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("evidence-upload-controller-");
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
    private EvidenceFileRepository evidenceFileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private MaterialBalanceRepository materialBalanceRepository;

    @Autowired
    private MultipartProperties multipartProperties;

    private String token;
    private Long uploaderId;
    private Long materialId;

    @BeforeEach
    void setUp() throws Exception {
        cleanAll();
        User uploader = userRepository.save(User.register(
                "upload-ctl@example.com", "{noop}x", "업로더", "010-3333-3333", UserRole.SUPPLIER));
        uploaderId = uploader.getId();
        token = jwtTokenService
                .issueAccessToken(
                        String.valueOf(uploaderId), List.of(uploader.getRole().name()))
                .value();
        materialId = materialRepository
                .save(Material.register(
                        "MAT-000904",
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

    @AfterEach
    void tearDown() throws Exception {
        cleanAll();
    }

    // ── multipart 파트 계약 ──────────────────────────────────

    @Test
    void acceptsTheFourMultipartPartsAndReturnsTheStoredEvidence() throws Exception {
        mockMvc.perform(upload().file(jpeg("before.jpg")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].evidenceFileId").isNumber())
                // 저장 경로는 요청이 아니라 서버가 만든다 — 클라이언트가 준 이름이
                // 경로에 섞이면 traversal 이 된다.
                .andExpect(jsonPath("$.data[0].storageKey")
                        .value(startsWith("MATERIAL/" + materialId + "/")))
                // 온체인 대조에 쓰는 값이라 그대로 내려간다. 0x + 64 hex.
                .andExpect(jsonPath("$.data[0].fileHash")
                        .value(matchesPattern("^0x[0-9a-f]{64}$")))
                .andExpect(jsonPath("$.data[0].evidenceType").value("BEFORE_PHOTO"))
                // 업로더는 폼이 아니라 JWT sub 에서 온다 — 요청으로 받으면 남의 이름으로
                // 증빙을 올릴 수 있다.
                .andExpect(jsonPath("$.data[0].uploadedBy").value(uploaderId))
                .andExpect(jsonPath("$.data[0].contentPath").exists())
                .andExpect(jsonPath("$.data[0].deletedAt").doesNotExist());

        assertThat(evidenceFileRepository.findAll()).hasSize(1);
        assertThat(filesOnDisk()).isEqualTo(1);
    }

    @Test
    void storesEveryFileOfAMultiFileRequest() throws Exception {
        mockMvc.perform(upload()
                        .file(jpeg("a.jpg"))
                        .file(jpeg("b.png", "image/png"))
                        .file(jpeg("slip.pdf", "application/pdf")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));

        assertThat(filesOnDisk()).isEqualTo(3);
    }

    /**
     * 파일 파트 이름은 {@code files} 다 — <b>프론트와의 고정 계약</b>이다.
     *
     * <p><b>현재 동작은 정책 문서 Error Contract 와 어긋난다.</b> 파트 이름이 다르거나
     * 아예 없으면 {@code @RequestParam("files")} 가 채워지지 않아 Spring 이
     * {@code MissingServletRequestPartException} 을 던지는데,
     * {@code GlobalExceptionHandler} 에 그 타입의 핸들러가 없어
     * {@code @ExceptionHandler(Exception.class)} 로 떨어진다 → <b>500
     * {@code INTERNAL_SERVER_ERROR}</b>.
     *
     * <p>기대는 400 이다. 잘못 보낸 쪽은 클라이언트이고,
     * {@code EvidenceErrorCode.EVIDENCE_FILE_MISSING}("올릴 파일이 없습니다.") 가 이미 그
     * 뜻으로 존재한다. 그런데 그 코드는 {@code EvidenceUploadService} 안에서
     * {@code files} 가 빈 리스트일 때만 나오고, HTTP 로는 파트가 하나도 없으면 컨트롤러에
     * 닿지 못하므로 <b>실제로 도달할 수 없다.</b>
     *
     * <p>결과가 500 이면 프론트는 "서버 장애" 로 보고 재시도 루프를 돌고, 우리는 장애
     * 알림을 받는다 — 원인은 요청 쪽인데. 이 테스트는 <b>지금의 동작을 기록</b>한다.
     * 고치는 판단은 코드 소유자에게 넘긴다 (티켓 지시).
     */
    @ParameterizedTest(name = "[{index}] files 파트 {0} → 현재 500 (기대: 400 EVIDENCE_FILE_MISSING)")
    @MethodSource("requestsWithoutAFilesPart")
    void reportsFiveHundredWhenTheFilesPartIsAbsent(String scenario, MockMultipartFile part)
            throws Exception {
        MockMultipartHttpServletRequestBuilder request = upload();
        if (part != null) {
            request.file(part);
        }

        mockMvc.perform(request)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));

        assertThat(evidenceFileRepository.findAll()).isEmpty();
        assertThat(filesOnDisk()).isZero();
    }

    private static Stream<Arguments> requestsWithoutAFilesPart() {
        return Stream.of(
                Arguments.of("이름이 file (단수)", file0("file", "before.jpg")),
                Arguments.of("이름이 evidenceFiles", file0("evidenceFiles", "before.jpg")),
                Arguments.of("파트 자체가 없음", null));
    }

    private static MockMultipartFile file0(String partName, String filename) {
        return new MockMultipartFile(partName, filename, "image/jpeg", JPEG);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(multipart("/api/evidence")
                        .file(jpeg("before.jpg"))
                        .param("referenceType", "MATERIAL")
                        .param("referenceId", String.valueOf(materialId))
                        .param("evidenceType", "BEFORE_PHOTO"))
                .andExpect(status().isUnauthorized());

        assertThat(evidenceFileRepository.findAll())
                .as("인증 없이 한 바이트도 저장되지 않는다")
                .isEmpty();
        assertThat(filesOnDisk()).isZero();
    }

    /** 폼 필드가 비면 파일을 쓰기 전에 공통 검증 봉투로 거절된다. */
    @ParameterizedTest(name = "[{index}] {0} 누락 → VALIDATION_ERROR")
    @MethodSource("requiredFormFields")
    void rejectsAMissingFormField(String omitted) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/evidence")
                .file(jpeg("before.jpg"));
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (!"referenceType".equals(omitted)) {
            request.param("referenceType", "MATERIAL");
        }
        if (!"referenceId".equals(omitted)) {
            request.param("referenceId", String.valueOf(materialId));
        }
        if (!"evidenceType".equals(omitted)) {
            request.param("evidenceType", "BEFORE_PHOTO");
        }

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(filesOnDisk()).isZero();
    }

    private static Stream<Arguments> requiredFormFields() {
        return Stream.of(
                Arguments.of("referenceType"),
                Arguments.of("referenceId"),
                Arguments.of("evidenceType"));
    }

    // ── Error Contract ──────────────────────────────────────

    /**
     * 정책 문서 Error Contract 의 400 네 가지가 HTTP 로 그대로 나가는지.
     *
     * <p>{@code code} 문자열이 프론트의 분기 기준이라 status 만으로는 부족하다.
     * 어느 층이 던지든 <b>봉투와 코드는 같아야 한다</b> — 실제로 아래 네 건은
     * {@code FileContentPolicy}(형식) 와 {@code EvidenceUploadService}(개수·크기) 로
     * 갈라져 있는데, 프론트는 그 차이를 몰라야 한다.
     */
    @ParameterizedTest(name = "[{index}] {0} → 400 {1}")
    @MethodSource("rejectedUploads")
    void rejectsBadUploadsWithThePolicyErrorCode(
            String scenario, String expectedCode, List<MockMultipartFile> files) throws Exception {
        MockMultipartHttpServletRequestBuilder request = upload();
        files.forEach(request::file);

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message").isString());

        assertThat(evidenceFileRepository.findAll()).isEmpty();
        assertThat(filesOnDisk())
                .as("거절된 요청은 디스크에 아무것도 남기지 않는다 — 아무도 가리키지 않는 고아가 된다")
                .isZero();
    }

    private static Stream<Arguments> rejectedUploads() {
        return Stream.of(
                Arguments.of(
                        "화이트리스트 밖 확장자",
                        "FILE_TYPE_NOT_ALLOWED",
                        List.of(file("report.exe", "application/octet-stream", JPEG))),
                Arguments.of(
                        "svg — 사용자에게는 이미지, 브라우저에는 스크립트 컨테이너",
                        "FILE_TYPE_NOT_ALLOWED",
                        List.of(file("logo.svg", "image/svg+xml", JPEG))),
                Arguments.of(
                        "확장자와 선언 타입 불일치 (.heic 를 .jpg 로 바꿔 올린 경우)",
                        "FILE_TYPE_MISMATCH",
                        List.of(file("photo.jpg", "image/heic", JPEG))),
                Arguments.of(
                        "pdf 이름에 이미지 타입",
                        "FILE_TYPE_MISMATCH",
                        List.of(file("slip.pdf", "image/jpeg", JPEG))),
                Arguments.of(
                        "빈 파일",
                        "FILE_EMPTY",
                        List.of(file("empty.jpg", "image/jpeg", new byte[0]))),
                Arguments.of(
                        "파일당 상한 초과 (10MB + 1바이트)",
                        "FILE_TOO_LARGE",
                        List.of(file("huge.jpg", "image/jpeg", new byte[(int) TEN_MEGABYTES + 1]))),
                Arguments.of(
                        "한 요청 11개",
                        "FILE_COUNT_EXCEEDED",
                        IntStream.range(0, 11)
                                .mapToObj(i -> file("photo" + i + ".jpg", "image/jpeg", JPEG))
                                .toList()));
    }

    /**
     * 위 {@code FILE_TOO_LARGE} 가 <b>2층 단독</b>이라는 근거.
     *
     * <p>테스트 프로파일의 {@code application.yml} 은 운영 파일을 <b>가린다</b>(대체가 아니라
     * 교체다). 그쪽에는 {@code spring.servlet.multipart} 설정이 없어 1층 값이 Spring Boot
     * 기본값(파일 1MB)으로 돌아간다. 그런데 위 테스트는 10MB+1 을 보내 <b>1MB 를 훨씬
     * 넘겼는데도</b> {@code FILE_TOO_LARGE} 를 2층에서 받는다 — MockMvc 가 실제 multipart
     * 파싱을 하지 않아 1층이 아예 관여하지 않기 때문이다.
     *
     * <p>이 단언이 없으면 "10MB 초과를 막았다" 를 1층 덕분이라고 오독할 수 있고, 2층 검사를
     * 지워도 테스트가 통과하는 것처럼 보인다. 1층은
     * {@code com.ssafy.upcycle.common.storage.FileSizeLimitLayerTests} 가 따로 본다.
     */
    @Test
    void theContainerLimitIsNotWhatRejectsTheOversizedFileHere() {
        assertThat(multipartProperties.getMaxFileSize())
                .as("테스트 프로파일에는 multipart 설정이 없다 — 1층은 기본값(1MB)이다")
                .isEqualTo(DataSize.ofMegabytes(1));
        assertThat(TEN_MEGABYTES)
                .as("보낸 파일은 1층 기본값보다 크다. 그런데도 2층 코드로 거절됐다")
                .isGreaterThan(multipartProperties.getMaxFileSize().toBytes());
    }

    /**
     * 상한값 자체는 받는다 — 10개, 정확히 10MB.
     *
     * <p>경계에서 하나 어긋난 구현({@code >=} 대신 {@code >})은 "휴대폰 사진이 안 올라간다"
     * 라는 형태로만 드러나고, 거절 케이스만 보는 테스트는 그것을 잡지 못한다.
     */
    @Test
    void acceptsExactlyTenFilesAndExactlyTenMegabytes() throws Exception {
        MockMultipartHttpServletRequestBuilder ten = upload();
        IntStream.range(0, 10).forEach(i -> ten.file(jpeg("photo" + i + ".jpg")));

        mockMvc.perform(ten)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(10));

        clean();

        mockMvc.perform(upload()
                        .file(file("exactly.jpg", "image/jpeg", new byte[(int) TEN_MEGABYTES])))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    /**
     * 업로드 스트림이 중간에 끊기면 {@code FILE_STORAGE_FAILED}(500) 다.
     *
     * <p>정책 문서 Error Contract 의 유일한 500 이고, 다른 여섯 개와 달리 <b>사용자가
     * 요청을 고쳐서 해결할 수 없는</b> 실패다 — 그래서 400 이 아니다. 저장소 빈을 모킹하지
     * 않고 실제 경로를 태운다: {@code MultipartFile#getInputStream} 이 던지는 것이
     * {@code EvidenceUploadService#store} 의 {@code IOException} 분기이고, 그것이 이 코드로
     * 번역되는 지점이 검증 대상이다.
     */
    @Test
    void reportsStorageFailureWhenTheUploadStreamBreaks() throws Exception {
        MockMultipartFile broken =
                new MockMultipartFile("files", "before.jpg", "image/jpeg", JPEG) {
                    @Override
                    public InputStream getInputStream() throws IOException {
                        throw new IOException("업로드 스트림이 끊겼다");
                    }
                };

        mockMvc.perform(upload().file(broken))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FILE_STORAGE_FAILED"));

        assertThat(evidenceFileRepository.findAll()).isEmpty();
        assertThat(filesOnDisk()).isZero();
    }

    /**
     * 뒤쪽 파일이 거절되면 <b>앞서 쓴 파일도 사라진다.</b>
     *
     * <p>{@code EvidenceUploadServiceTests} 가 같은 것을 서비스 층에서 본다. 여기서 다시
     * 보는 이유는 정리가 트랜잭션이 아니라 {@code catch} 블록의 명시적 삭제라서다 —
     * MVC 층에서 예외가 다르게 잡히면(예: 다른 {@code @ExceptionHandler} 가 먼저 먹으면)
     * 서비스 테스트는 통과하는데 실제 요청에서는 고아 파일이 남을 수 있다.
     */
    @Test
    void leavesNothingBehindWhenALaterFileIsRejected() throws Exception {
        mockMvc.perform(upload()
                        .file(jpeg("before.jpg"))
                        .file(file("logo.svg", "image/svg+xml", JPEG)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_TYPE_NOT_ALLOWED"));

        assertThat(filesOnDisk()).isZero();
        assertThat(evidenceFileRepository.findAll()).isEmpty();
    }

    /** 없는 대상에는 붙지 않는다 — {@code reference_id} 에 FK 가 없어 DB 가 막지 못한다. */
    @Test
    void rejectsAReferenceThatDoesNotExist() throws Exception {
        mockMvc.perform(multipart("/api/evidence")
                        .file(jpeg("before.jpg"))
                        .param("referenceType", "MATERIAL")
                        .param("referenceId", String.valueOf(materialId + 999))
                        .param("evidenceType", "BEFORE_PHOTO")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVIDENCE_REFERENCE_NOT_FOUND"));

        assertThat(filesOnDisk()).isZero();
    }

    // ── 도우미 ──────────────────────────────────────────────

    private MockMultipartHttpServletRequestBuilder upload() {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/evidence");
        request.param("referenceType", "MATERIAL")
                .param("referenceId", String.valueOf(materialId))
                .param("evidenceType", "BEFORE_PHOTO")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }

    private static MockMultipartFile jpeg(String name) {
        return file(name, "image/jpeg", JPEG);
    }

    private static MockMultipartFile jpeg(String name, String contentType) {
        return file(name, contentType, JPEG);
    }

    private static MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("files", name, contentType, content);
    }

    private long filesOnDisk() throws IOException {
        try (Stream<Path> walk = Files.walk(STORAGE_ROOT)) {
            return walk.filter(Files::isRegularFile).count();
        }
    }

    /**
     * 증빙 행과 디스크만 비운다. 사용자·소재는 남긴다 — 한 테스트 안에서 두 번 업로드하며
     * 개수를 단언할 때 쓴다.
     *
     * <p>{@code @SpringBootTest} 는 트랜잭션 롤백이 없어 커밋한 행이 같은 in-memory H2 에
     * 남고, 디스크는 애초에 되돌아가지 않는다. 비우지 않으면 "실패했는데 파일이 없다" 를
     * 확인하는 단언이 거짓으로 통과한다.
     */
    private void clean() throws IOException {
        evidenceFileRepository.deleteAll();
        try (Stream<Path> walk = Files.walk(STORAGE_ROOT)) {
            walk.filter(Files::isRegularFile).forEach(file -> {
                try {
                    Files.delete(file);
                } catch (IOException ignored) {
                    // 지우지 못하면 개수 단언이 실패해 드러난다. 여기서 감추지 않는다.
                }
            });
        }
    }

    private void cleanAll() throws IOException {
        clean();
        materialBalanceRepository.deleteAll();
        materialRepository.deleteAll();
        userRepository.deleteAll();
    }
}
