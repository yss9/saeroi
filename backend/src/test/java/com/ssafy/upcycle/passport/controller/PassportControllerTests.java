package com.ssafy.upcycle.passport.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductModelRepository productModelRepository;

    @Autowired
    private ProductItemRepository productItemRepository;

    private ProductModel model;

    @BeforeEach
    void setUp() {
        User brand = userRepository.save(User.register(
                "passport-brand@example.com", "{noop}x", "브랜드", "010-5555-5555", UserRole.BRAND));
        model = productModelRepository.save(ProductModel.register(
                "RE:BAG", ProductCategory.BAG, brand.getId(), 129_000, "폐데님으로 만든 가방", "thumb.jpg"));
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
                .andExpect(jsonPath("$.data.product.thumbnailUrl").value("thumb.jpg"))
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

    private ProductItem produceItem() {
        String qrToken = UUID.randomUUID().toString().replace("-", "");
        String itemCode = "ITEM-" + UUID.randomUUID().toString().substring(0, 20);
        return productItemRepository.save(ProductItem.produce(1L, model.getId(), 1, itemCode, qrToken));
    }
}
