package com.ssafy.upcycle.prediction;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.common.storage.FileContent;
import com.ssafy.upcycle.common.storage.FileStorageService;
import com.ssafy.upcycle.prediction.entity.PredictionMarket;
import com.ssafy.upcycle.prediction.repository.PredictionBucketRepository;
import com.ssafy.upcycle.prediction.repository.PredictionMarketRepository;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 소비자 예측시장의 제품 사진.
 *
 * <p>시장은 개설할 때 제품 사진을 <b>저장 키</b>로 굳혀 둔다. 그 키를 그대로 내려주면 화면이
 * {@code <img src="products/...">} 로 그려 사진이 깨진다. 쇼핑몰의 공개 썸네일은 판매 중인
 * 제품만 열어 주므로, 판매 시작 전에 여는 예측시장에는 쓸 수 없다 — 시장 번호로 여는 주소를 따로 둔다.
 */
@SpringBootTest(properties = {
        "app.processing.expiry.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:prediction-thumbnail-tests;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class PredictionMarketThumbnailTests {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    @Autowired MockMvc mvc;
    @MockitoBean PredictionMarketRepository markets;
    @MockitoBean PredictionBucketRepository buckets;
    @MockitoBean FileStorageService storage;

    private static PredictionMarket market(long id, String thumbnail) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 28, 9, 0);
        PredictionMarket market = PredictionMarket.create(1, 20L, 35, 31L, "Wallet", thumbnail, 7L,
                "0x2222222222222222222222222222222222222222", 30_000, 10,
                now.plusDays(1), now.plusDays(1), now.plusDays(8), "0x" + "a".repeat(64));
        ReflectionTestUtils.setField(market, "id", id);
        return market;
    }

    @Test
    void listGivesAnImageAddressInsteadOfTheStorageKey() throws Exception {
        when(markets.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(
                market(5L, "products/2026/09/wallet.png"),
                // 데모 상품은 프론트에 포함된 정적 이미지다. 그대로 둔다.
                market(6L, "/images/landing/product-denim-circle.webp"),
                market(7L, null)));
        when(buckets.findAllByMarketIdOrderByBucketIndex(anyLong())).thenReturn(List.of());

        mvc.perform(get("/api/v1/prediction-markets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data[0].productThumbnailUrl").value("/api/v1/prediction-markets/5/thumbnail"))
                .andExpect(jsonPath("data[1].productThumbnailUrl").value("/images/landing/product-denim-circle.webp"))
                .andExpect(jsonPath("data[2].productThumbnailUrl").doesNotExist());
    }

    @Test
    void thumbnailIsServedWithoutLoginLikeTheMarketList() throws Exception {
        when(markets.findById(5L)).thenReturn(Optional.of(market(5L, "products/2026/09/wallet.png")));
        when(storage.read("products/2026/09/wallet.png"))
                .thenReturn(new FileContent(new ByteArrayInputStream(PNG), PNG.length, "image/png"));

        mvc.perform(get("/api/v1/prediction-markets/5/thumbnail"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG));
    }

    @Test
    void marketWithoutAPhotoAnswersNotFound() throws Exception {
        when(markets.findById(7L)).thenReturn(Optional.of(market(7L, null)));

        mvc.perform(get("/api/v1/prediction-markets/7/thumbnail"))
                .andExpect(status().isNotFound());
    }
}
