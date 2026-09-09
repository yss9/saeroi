package com.ssafy.upcycle.passport.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.common.storage.FileReference;
import com.ssafy.upcycle.common.storage.FileStorageService;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * QR Passport 공개 조회. 고정하는 계약이 셋이다 — <b>인증 없이 열린다</b>,
 * <b>거래 정보(브랜드 id·가격)를 담지 않는다</b>, <b>모르는 토큰은 404 다</b>.
 *
 * <p>두 번째가 이 기능의 핵심이다. §6-3(2026-09-08)이 "보유자·단가·거래 상대" 를
 * 공개 응답에서 빼기로 결정했는데, 여기서 값이 새면 그 결정이 코드에 반영되지 않은 것이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PassportControllerTests {

    private static final String PATH = "/api/v1/qr";

    /**
     * {@code ProductModelControllerTests} 와 같은 이유로 임시 디렉터리를 쓴다 — 기본
     * 저장 루트(`/app/files`)에 그대로 두면 테스트가 레포 밖에 파일을 쓴다.
     */
    private static final Path STORAGE_ROOT = createStorageRoot();

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("passport-controller-");
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
    private UserRepository userRepository;

    @Autowired
    private ProductModelRepository productModelRepository;

    @Autowired
    private ProductItemRepository productItemRepository;

    @Autowired
    private FileStorageService fileStorageService;

    private ProductModel model;

    @BeforeEach
    void setUp() {
        User brand = userRepository.save(User.register(
                "passport-brand@example.com", "{noop}x", "브랜드", "010-5555-5555", UserRole.BRAND));
        String thumbnailKey = storeThumbnail(brand.getId());
        model = productModelRepository.save(ProductModel.register(
                "RE:BAG", ProductCategory.BAG, brand.getId(), 129_000, "폐데님으로 만든 가방", thumbnailKey));
    }

    private String storeThumbnail(Long brandId) {
        byte[] bytes = "photo-bytes".getBytes(StandardCharsets.UTF_8);
        return fileStorageService.store(
                        new FileReference("PRODUCT_IMAGE", brandId),
                        "thumb.jpg",
                        "image/jpeg",
                        new ByteArrayInputStream(bytes),
                        bytes.length)
                .storageKey();
    }

    @Test
    void requiresNoAuthentication() throws Exception {
        // SecurityConfig 의 permitAll 에서 이 경로가 빠지면 여기서 401 로 깨진다.
        ProductItem item = produceItem();

        mockMvc.perform(get(PATH + "/{qrToken}", item.getQrToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void returnsThePublicProductInfo() throws Exception {
        ProductItem item = produceItem();

        mockMvc.perform(get(PATH + "/{qrToken}", item.getQrToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemCode").value(item.getItemCode()))
                .andExpect(jsonPath("$.data.serialNumber").value(item.getSerialNumber()))
                .andExpect(jsonPath("$.data.itemStatus").value("IN_STOCK"))
                .andExpect(jsonPath("$.data.product.name").value("RE:BAG"))
                .andExpect(jsonPath("$.data.product.category").value("BAG"))
                .andExpect(jsonPath("$.data.traceabilityAvailable").value(false));
    }

    @Test
    void doesNotLeakTradeInformation() throws Exception {
        // brandId·price 는 §6-3 이 명시적으로 뺀 "거래 정보" 다. 응답 어디에도 있으면 안 된다.
        ProductItem item = produceItem();

        mockMvc.perform(get(PATH + "/{qrToken}", item.getQrToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.brandId").doesNotExist())
                .andExpect(jsonPath("$.data.price").doesNotExist())
                .andExpect(jsonPath("$.data.product.brandId").doesNotExist())
                .andExpect(jsonPath("$.data.product.price").doesNotExist())
                .andExpect(jsonPath("$.data.product.thumbnailUrl").doesNotExist())
                .andExpect(jsonPath("$.data.productItemId").doesNotExist())
                .andExpect(jsonPath("$.data.productModelId").doesNotExist());
    }

    @Test
    void rejectsAnUnknownQrToken() throws Exception {
        // 모르는 토큰과 "형식은 맞는데 없는 토큰" 을 구분해 알려주지 않는다 — 토큰 자체가
        // 접근 통제라 실패 사유가 자세하면 무작위 시도에 힌트를 준다.
        mockMvc.perform(get(PATH + "/{qrToken}", "0".repeat(32)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QR_TOKEN_NOT_FOUND"));
    }

    // ── 썸네일 ──────────────────────────────────────────────

    @Test
    void thumbnailRequiresNoAuthenticationEither() throws Exception {
        ProductItem item = produceItem();

        mockMvc.perform(get(PATH + "/{qrToken}/thumbnail", item.getQrToken()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE))
                .andExpect(content().bytes("photo-bytes".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void thumbnailIsPubliclyCacheable() throws Exception {
        // 로그인 사용자별 응답이 아니라 qrToken 하나에 항상 같은 바이트라, 마켓 썸네일과
        // 달리 공용 캐시가 저장해도 안전하다.
        ProductItem item = produceItem();

        mockMvc.perform(get(PATH + "/{qrToken}/thumbnail", item.getQrToken()))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=3600"));
    }

    @Test
    void thumbnailForAnUnknownQrTokenIs404() throws Exception {
        mockMvc.perform(get(PATH + "/{qrToken}/thumbnail", "0".repeat(32)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QR_TOKEN_NOT_FOUND"));
    }

    private ProductItem produceItem() {
        String qrToken = UUID.randomUUID().toString().replace("-", "");
        String itemCode = "ITEM-" + UUID.randomUUID().toString().substring(0, 20);
        return productItemRepository.save(ProductItem.produce(1L, model.getId(), 1, itemCode, qrToken));
    }
}
