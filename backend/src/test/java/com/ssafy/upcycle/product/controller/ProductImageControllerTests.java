package com.ssafy.upcycle.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.common.storage.FileStorageService;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제품 이미지 업로드와 조회의 <b>HTTP 표면</b>.
 *
 * <p>파일 형식 판정 자체는 {@code FileContentPolicyTests} 가 이미 본다. 여기서 고정하는
 * 것은 그 판정이 multipart 요청을 거쳐 어떤 응답으로 나가는가, 그리고 <b>업로드 → 등록 →
 * 조회</b> 가 실제로 이어지는가다. 셋이 각각 통과해도 이어지지 않을 수 있다 — 등록이
 * 저장 키를 그대로 보관하지 않거나, 조회가 다른 키를 만들어 읽으면 그렇게 된다.
 *
 * <p>{@code @RequestParam("file")} 파트 이름이 프론트와의 계약이라 이름이 하나만 틀려도
 * 컴파일은 되고 서비스 테스트도 통과하는데 프론트만 400 을 받는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductImageControllerTests {

    private static final String UPLOAD_PATH = "/api/v1/product-images";
    private static final String MODEL_PATH = "/api/v1/product-models";
    private static final byte[] JPEG = "photo-bytes".getBytes(StandardCharsets.UTF_8);

    /**
     * 저장 루트를 임시 디렉터리로 돌린다. 기본값이 {@code /app/files} 라 그대로 두면
     * 테스트가 레포 밖 실제 경로에 파일을 쓴다.
     */
    private static final Path STORAGE_ROOT = createStorageRoot();

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("product-image-controller-");
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
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileStorageService fileStorageService;

    private User brand;
    private String brandToken;

    @BeforeEach
    void setUp() {
        brand = userRepository.save(User.register(
                "product-image-brand@example.com", "{noop}x", "브랜드", "010-0000-0000", UserRole.BRAND));
        brandToken = tokenFor(brand);
    }

    // ── 업로드 권한 ──────────────────────────────────────────

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(multipart(UPLOAD_PATH).file(jpeg()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsASupplierAccount() throws Exception {
        // 업로드만 열어 두면 아무 계정이나 우리 저장소에 파일을 쌓을 수 있다.
        // 등록에서 걸러도 파일은 이미 디스크에 있다.
        User supplier = userRepository.save(User.register(
                "product-image-supplier@example.com", "{noop}x", "공급자", "010-1111-1111",
                UserRole.SUPPLIER));

        upload(tokenFor(supplier), jpeg())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCT_ROLE_NOT_ALLOWED"));
    }

    @Test
    void rejectsASuspendedBrand() throws Exception {
        String tokenIssuedBeforeSuspension = tokenFor(brand);
        brand.suspend();
        userRepository.save(brand);

        upload(tokenIssuedBeforeSuspension, jpeg())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCT_BRAND_SUSPENDED"));
    }

    // ── 업로드 형식 ──────────────────────────────────────────

    @Test
    void uploadsAJpegAndReturnsItsStorageKey() throws Exception {
        String response = upload(brandToken, jpeg())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.data.size").value(JPEG.length))
                // 해시는 0x + 64자다. 온체인 비교 형식과 같다.
                .andExpect(jsonPath("$.data.hash").value(startsWith("0x")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String key = storageKey(response);
        // 경로가 브랜드별로 갈린다. 남의 폴더에 쓰이지 않는다는 뜻이다.
        assertThat(key).startsWith("PRODUCT_IMAGE/" + brand.getId() + "/");
        assertThat(fileStorageService.exists(key)).isTrue();
    }

    @Test
    void rejectsAPdf() throws Exception {
        // 공용 정책은 PDF 를 받는다 — 증빙에 계량증명서가 있기 때문이다.
        // 제품 사진은 마켓에 <img> 로 걸리므로 그보다 좁다.
        MockMultipartFile pdf =
                new MockMultipartFile("file", "spec.pdf", "application/pdf", JPEG);

        upload(brandToken, pdf)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_IMAGE_TYPE_NOT_ALLOWED"));
    }

    @Test
    void rejectsAnExtensionThatDisagreesWithTheContentType() throws Exception {
        // 이름만 보면 photo.jpg 라는 이름의 무엇이든 통과한다.
        MockMultipartFile disguised =
                new MockMultipartFile("file", "photo.jpg", "image/png", JPEG);

        upload(brandToken, disguised)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_TYPE_MISMATCH"));
    }

    @Test
    void rejectsAnEmptyFile() throws Exception {
        MockMultipartFile empty =
                new MockMultipartFile("file", "thumb.jpg", "image/jpeg", new byte[0]);

        upload(brandToken, empty)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_IMAGE_MISSING"));
    }

    // ── 업로드 → 등록 → 조회 ─────────────────────────────────

    @Test
    void servesTheImageThatWasRegisteredWithTheModel() throws Exception {
        // 셋이 각각 통과해도 이어지지 않을 수 있다. 이 테스트가 그 연결을 고정한다.
        String key = storageKey(upload(brandToken, jpeg()).andReturn().getResponse().getContentAsString());
        Long modelId = registerModel(key);

        byte[] served = mockMvc.perform(get(MODEL_PATH + "/{id}/thumbnail", modelId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                // 브라우저가 타입을 추측하면 화이트리스트가 무의미해진다.
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                // 로그인 뒤에만 보이는 자료라 공용 프록시가 캐시하면 안 된다.
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, max-age=0, no-store"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(served).isEqualTo(JPEG);
    }

    @Test
    void requiresAuthenticationToViewAThumbnail() throws Exception {
        // 마켓 자체가 로그인 뒤에 보이므로 이미지만 열어 둘 이유가 없다.
        String key = storageKey(upload(brandToken, jpeg()).andReturn().getResponse().getContentAsString());
        Long modelId = registerModel(key);

        mockMvc.perform(get(MODEL_PATH + "/{id}/thumbnail", modelId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAThumbnailRequestForAnUnknownModel() throws Exception {
        mockMvc.perform(get(MODEL_PATH + "/{id}/thumbnail", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_MODEL_NOT_FOUND"));
    }

    @Test
    void reportsAMissingFileSeparatelyFromAMissingProduct() throws Exception {
        // 행은 있는데 파일이 사라졌다. FILE_NOT_FOUND 를 그대로 흘리면 프론트가
        // "제품이 없다" 와 구분할 수 없다.
        String key = storageKey(upload(brandToken, jpeg()).andReturn().getResponse().getContentAsString());
        Long modelId = registerModel(key);
        fileStorageService.delete(key);

        mockMvc.perform(get(MODEL_PATH + "/{id}/thumbnail", modelId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_THUMBNAIL_NOT_FOUND"));
    }

    // ── helpers ─────────────────────────────────────────────

    private MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "thumb.jpg", "image/jpeg", JPEG);
    }

    private ResultActions upload(String token, MockMultipartFile file) throws Exception {
        return mockMvc.perform(multipart(UPLOAD_PATH)
                .file(file)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private String storageKey(String uploadResponse) throws Exception {
        return objectMapper.readTree(uploadResponse).path("data").path("storageKey").asText();
    }

    private Long registerModel(String thumbnailKey) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("name", "RE:BAG");
        request.put("category", "BAG");
        request.put("price", 129_000);
        request.put("description", "폐데님으로 만든 가방");
        request.put("thumbnailUrl", thumbnailKey);

        String response = mockMvc.perform(post(MODEL_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("productModelId").asLong();
    }

    private String tokenFor(User user) {
        return jwtTokenService
                .issueAccessToken(String.valueOf(user.getId()), List.of(user.getRole().name()))
                .value();
    }
}
