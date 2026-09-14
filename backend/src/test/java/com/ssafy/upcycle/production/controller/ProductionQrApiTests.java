package com.ssafy.upcycle.production.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개체 QR 이미지 조회. <b>라벨 인쇄가 쓰는 경로다.</b>
 *
 * <p>고정하는 계약이 넷이다 — <b>배치 소유자만</b>, <b>그 배치의 개체만</b>,
 * <b>스캔하면 Passport 주소가 나온다</b>, <b>크기 범위를 벗어나면 400</b>.
 *
 * <p>세 번째가 핵심이다. 응답이 PNG 라는 것만 확인하면 스캔이 안 되는 이미지를 수십 장
 * 인쇄한 뒤에야 알게 된다 — 그때는 물건에 이미 붙어 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductionQrApiTests {

    private static final String PATH = "/api/v1/production-batches";
    private static final String WALLET = "0x1234567890123456789012345678901234567890";
    private static final long CHAIN_ID = 31221L;
    private static final long HELD_QUANTITY = 100_000L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserWalletRepository walletRepository;

    @Autowired
    private ProductModelRepository productModelRepository;

    @Autowired
    private ProductItemRepository productItemRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private MaterialBalanceRepository balanceRepository;

    private User brand;
    private String brandToken;
    private Long productModelId;
    private Long materialId;
    private Long batchId;
    private ProductItem item;

    @BeforeEach
    void setUp() throws Exception {
        brand = userRepository.save(User.register(
                "qr-brand@example.com", "{noop}x", "브랜드", "010-0000-0000", UserRole.BRAND));
        brandToken = tokenFor(brand);
        walletRepository.save(UserWallet.link(brand.getId(), WALLET, CHAIN_ID));
        productModelId = productModelRepository.save(ProductModel.register(
                        "RE:BAG", ProductCategory.BAG, brand.getId(), 129_000L, "설명", "key.jpg"))
                .getId();
        materialId = heldMaterial();
        batchId = confirmedBatch();
        item = productItemRepository.findAllByProductionBatchId(batchId).get(0);
    }

    // ── 이미지 ──────────────────────────────────────────────

    @Test
    void servesAScannablePngThatOpensThePassport() throws Exception {
        // 응답이 PNG 라는 것만 보면 스캔이 안 되는 이미지를 인쇄한 뒤에야 알게 된다.
        byte[] png = qr(null)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        // 접두어를 여기 적지 않는다 — 그 값은 설정이고, 배포마다 다르다.
        // 확인할 것은 "스캔하면 이 개체의 토큰이 붙은 주소가 나온다" 이다.
        assertThat(decodeQr(png)).endsWith("/" + item.getQrToken());
    }

    @Test
    void cachesHardBecauseTheImageNeverChanges() throws Exception {
        // 토큰이 바뀌지 않는 한 같은 개체의 QR 은 영원히 같다. 라벨 수십 장을
        // 미리보기로 띄울 때 재요청이 나가지 않아야 한다.
        qr(null)
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL, "private, max-age=31536000, immutable"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void honoursTheRequestedSize() throws Exception {
        byte[] png = qr(256).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(ImageIO.read(new ByteArrayInputStream(png)).getWidth()).isEqualTo(256);
    }

    @Test
    void refusesASizeOutsideTheAllowedRange() throws Exception {
        // 생성 모듈은 IllegalArgumentException 을 던진다. 그대로 두면 500 인데,
        // 크기는 사용자가 고칠 수 있는 입력이다.
        qr(64)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCTION_QR_SIZE_INVALID"));
        qr(4096)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCTION_QR_SIZE_INVALID"));
    }

    // ── 권한 ────────────────────────────────────────────────

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(PATH + "/{b}/items/{i}/qr", batchId, item.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refusesAnotherBrandsBatch() throws Exception {
        // QR 은 공개 페이지의 열쇠다. 남이 라벨을 뽑아 갈 수 있으면 안 된다.
        User otherBrand = userRepository.save(User.register(
                "qr-other@example.com", "{noop}x", "다른브랜드", "010-3333-3333", UserRole.BRAND));

        mockMvc.perform(get(PATH + "/{b}/items/{i}/qr", batchId, item.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(otherBrand)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCTION_FORBIDDEN"));
    }

    @Test
    void refusesAnItemFromAnotherBatch() throws Exception {
        // 소유자 검사를 통과했더라도 개체 번호만 믿으면 남의 배치 개체 라벨을 받아 갈 수 있다.
        Long otherBatchId = confirmedBatch(heldMaterial());
        ProductItem stranger = productItemRepository.findAllByProductionBatchId(otherBatchId).get(0);

        mockMvc.perform(get(PATH + "/{b}/items/{i}/qr", batchId, stranger.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCTION_ITEM_NOT_FOUND"));
    }

    @Test
    void rejectsAnUnknownItem() throws Exception {
        mockMvc.perform(get(PATH + "/{b}/items/{i}/qr", batchId, 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCTION_ITEM_NOT_FOUND"));
    }

    // ── 개체 목록의 qrUrl ────────────────────────────────────

    @Test
    void theItemListCarriesTheSameUrlTheImageEncodes() throws Exception {
        // 프론트가 토큰에 접두어를 붙여 만들면 그 규칙이 두 곳이 되고 언젠가 한쪽만 바뀐다.
        String listed = objectMapper.readTree(
                        mockMvc.perform(get(PATH + "/{id}/items", batchId)
                                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                .path("data").get(0).path("qrUrl").asText();

        byte[] png = qr(null).andReturn().getResponse().getContentAsByteArray();
        assertThat(decodeQr(png)).isEqualTo(listed);
    }

    // ── helpers ─────────────────────────────────────────────

    private ResultActions qr(Integer size) throws Exception {
        String url = PATH + "/" + batchId + "/items/" + item.getId() + "/qr"
                + (size == null ? "" : "?size=" + size);
        return mockMvc.perform(get(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken));
    }

    private String decodeQr(byte[] png) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        return new QRCodeReader()
                .decode(new BinaryBitmap(
                        new HybridBinarizer(new BufferedImageLuminanceSource(image))))
                .getText();
    }

    private Long confirmedBatch() throws Exception {
        return confirmedBatch(materialId);
    }

    private Long confirmedBatch(Long material) throws Exception {
        Map<String, Object> open = new HashMap<>();
        open.put("batchName", "RE:BAG 1차 생산");
        open.put("productModelId", productModelId);
        open.put("producedQuantity", 2);

        String response = mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(open)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long id = objectMapper.readTree(response).path("data").path("productionBatchId").asLong();

        Map<String, Object> input = new HashMap<>();
        input.put("materialId", material);
        input.put("inputQuantity", 30_000L);
        input.put("productUsedQuantity", 30_000L);
        input.put("reusableScrapQuantity", 0L);
        input.put("wasteQuantity", 0L);

        mockMvc.perform(post(PATH + "/{id}/inputs", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("inputs", List.of(input)))))
                .andExpect(status().isOk());
        mockMvc.perform(post(PATH + "/{id}/confirm", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk());
        return id;
    }

    private Long heldMaterial() {
        Material material = materialRepository.save(Material.register(
                "MAT-" + UUID.randomUUID().toString().substring(0, 10),
                MaterialType.DENIM,
                MaterialRootOrigin.VIRGIN,
                "원단",
                null,
                "A",
                HELD_QUANTITY,
                null,
                null,
                brand.getId()));
        balanceRepository.save(
                MaterialBalance.of(material.getId(), brand.getId(), WALLET, HELD_QUANTITY));
        return material.getId();
    }

    private String body(Map<String, Object> request) throws Exception {
        return objectMapper.writeValueAsString(request);
    }

    private String tokenFor(User user) {
        return jwtTokenService
                .issueAccessToken(String.valueOf(user.getId()), List.of(user.getRole().name()))
                .value();
    }
}
