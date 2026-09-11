package com.ssafy.upcycle;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.processing.repository.ProcessingRequestInputRepository;
import com.ssafy.upcycle.processing.repository.ProcessingRequestRepository;
import com.ssafy.upcycle.trade.repository.MaterialListingRepository;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
import com.ssafy.upcycle.trade.repository.TradeShippingRepository;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import com.ssafy.upcycle.user.geolocation.AddressGeocoder;
import com.ssafy.upcycle.user.geolocation.GeoCoordinates;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * <b>Role 별로 자기 여정을 끝까지 걸을 수 있는가.</b>
 *
 * <p>기존 테스트는 전부 도메인 단위다 — 소재는 소재만, 거래는 거래만 본다. 그래서 각각은
 * 통과하는데 <b>이어 붙이면 끊기는</b> 자리가 보이지 않는다. 실제로 그렇게 놓친 것이 있었다:
 * 소재 등록이 보유 행을 만들지 않아 <b>등록한 소재를 팔 수 없었다.</b> 소재 테스트는 저장까지만
 * 보고, 마켓 테스트는 보유 행을 직접 만들어 두고 판매 등록을 봐서 양쪽 다 통과했다.
 *
 * <p>그래서 이 파일은 도메인이 아니라 <b>사람</b>을 따라간다. 공급자가 가입해서 물건을 팔고,
 * 브랜드가 사서 받고, 가공업체가 의뢰를 보는 하나의 이야기를 API 로만 걷는다. 시드 SQL 로
 * 중간 상태를 채워 넣지 않는 것이 핵심이다 — 채워 넣는 순간 무엇이 서버가 만든 것이고
 * 무엇이 시드가 만든 것인지 구분이 사라진다.
 *
 * <h2>여기 없는 것이 곧 없는 기능이다</h2>
 *
 * <p>회원가입·로그인이 시드로 대체되어 있는 것은 <b>API 가 없기 때문</b>이고, CONSUMER 와
 * ADMIN 의 여정이 비어 있는 것도 같은 이유다. 이 테스트가 커버하지 못하는 범위가 곧 지금
 * 구현되지 않은 범위이며, 기능이 붙을 때마다 여기에 한 단계씩 늘어나야 한다.
 *
 * <p>{@code @Transactional} 을 붙이지 않았다. 각 요청이 별도 트랜잭션에서 커밋한 상태를
 * 다음 요청이 보는 것이 실제 흐름이고, 한 트랜잭션에 묶으면 커밋되지 않은 상태로 통과하는
 * 테스트가 된다. 대신 앞뒤로 직접 정리한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Role 별 사용자 여정")
class RoleJourneyTests {

    private static final String SUPPLIER_WALLET = "0xa11ce00000000000000000000000000000000001";
    private static final String BRAND_WALLET = "0xb0b0000000000000000000000000000000000002";
    private static final String PROCESSOR_WALLET = "0xc0de000000000000000000000000000000000003";
    private static final long SSAFY_CHAIN_ID = 31221L;
    private static final String PASSWORD = "Journey1234!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;

    @MockitoBean private AddressGeocoder addressGeocoder;

    @Autowired private UserRepository userRepository;
    @Autowired private UserWalletRepository walletRepository;
    @Autowired private CompanyProfileRepository companyProfileRepository;
    @Autowired private MaterialRepository materialRepository;
    @Autowired private MaterialBalanceRepository balanceRepository;
    @Autowired private MaterialLockRepository lockRepository;
    @Autowired private MaterialListingRepository listingRepository;
    @Autowired private MaterialTradeRepository tradeRepository;
    @Autowired private TradeShippingRepository shippingRepository;
    @Autowired private ProcessingRequestRepository processingRequestRepository;
    @Autowired private ProcessingRequestInputRepository processingInputRepository;

    private String supplier;
    private String brand;
    private String processor;
    private String consumer;
    private Long processorId;

    /**
     * <b>가입부터 시작한다.</b> 계정을 리포지토리로 직접 만들지 않는다 — 그러면 회원가입이
     * 깨져도 이 테스트는 통과한다. 실제 사용자는 화면에서 가입하고 로그인해서 들어온다.
     *
     * <p>지갑만 리포지토리로 붙인다. 지갑 등록은 서명이 필요한 경로라
     * ({@code POST /api/v1/wallets} 가 nonce·서명을 요구한다) 테스트에서 재현하려면
     * 개인키로 서명해야 하고, 그 경로는 {@code WalletControllerTests} 가 이미 본다.
     */
    @BeforeEach
    void setUp() throws Exception {
        clean();
        when(addressGeocoder.geocode(anyString())).thenReturn(new GeoCoordinates(
                new BigDecimal("37.5012345"), new BigDecimal("127.0123456")));
        supplier = signUpBusinessAndLogin("journey-supplier@test", UserRole.SUPPLIER, SUPPLIER_WALLET);
        brand = signUpBusinessAndLogin("journey-brand@test", UserRole.BRAND, BRAND_WALLET);
        processor = signUpBusinessAndLogin("journey-processor@test", UserRole.PROCESSOR, PROCESSOR_WALLET);
        processorId = userRepository.findByEmail("journey-processor@test").orElseThrow().getId();
        consumer = signUpConsumerAndLogin("journey-consumer@test");
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Nested
    @DisplayName("SUPPLIER — 등록하고 팔고 보낸다")
    class SupplierJourney {

        /**
         * 공급자 여정 전체. <b>중간 상태를 하나도 심지 않는다</b> — 각 단계의 입력은 앞
         * 단계의 응답에서만 나온다.
         */
        @Test
        void walksFromRegistrationToShipment() throws Exception {
            long materialId = registerMaterial(supplier, "저니 데님", 30L);

            // 등록만 했는데 보유 소재에 전량이 보여야 한다. 이 단이 비어 있으면 판매 등록이
            // 가용량 0 으로 막힌다 — 실제로 막혀 있던 지점이다.
            mockMvc.perform(get("/api/materials/me").header(HttpHeaders.AUTHORIZATION, supplier))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].materialId").value(materialId))
                    .andExpect(jsonPath("$.data[0].available").value(30_000));

            long listingId = createListing(supplier, materialId, 30L, 4_000L);

            // 판매에 건 만큼 가용량이 줄어야 한다. 잠금이 안 걸리면 같은 소재를 두 번 팔 수 있다.
            mockMvc.perform(get("/api/materials/me").header(HttpHeaders.AUTHORIZATION, supplier))
                    .andExpect(jsonPath("$.data[0].available").value(0));

            // 브랜드가 사고 결제까지 마쳐야 발송할 것이 생긴다.
            long tradeId = createTrade(brand, listingId, 10L);
            payFor(brand, tradeId);

            mockMvc.perform(patch("/api/v1/trades/{id}/ship", tradeId)
                            .header(HttpHeaders.AUTHORIZATION, supplier)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("carrier", "CJ대한통운", "trackingNumber", "1234567890"))))
                    .andExpect(status().isOk());
        }

        /** 판매는 보유량 안에서만 된다. 위 여정이 우연히 통과한 것이 아님을 보인다. */
        @Test
        void cannotListMoreThanRegistered() throws Exception {
            long materialId = registerMaterial(supplier, "초과 판매 시도", 10L);

            mockMvc.perform(post("/api/v1/listings")
                            .header(HttpHeaders.AUTHORIZATION, supplier)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("materialId", materialId, "quantity", 11,
                                    "unit", "KG", "pricePerKg", 4_000))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("LISTING_QUANTITY_EXCEEDS_AVAILABLE"));
        }

        /** 남의 거래는 보낼 수 없다. 역할이 아니라 그 거래의 판매자인지로 판정한다. */
        @Test
        void cannotShipSomeoneElsesTrade() throws Exception {
            String otherToken = signUpBusinessAndLogin(
                    "journey-other-supplier@test", UserRole.SUPPLIER,
                    "0xdead000000000000000000000000000000000009");

            long materialId = registerMaterial(supplier, "남의 거래", 30L);
            long listingId = createListing(supplier, materialId, 30L, 4_000L);
            long tradeId = createTrade(brand, listingId, 10L);
            payFor(brand, tradeId);

            mockMvc.perform(patch("/api/v1/trades/{id}/ship", tradeId)
                            .header(HttpHeaders.AUTHORIZATION, otherToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("carrier", "CJ대한통운", "trackingNumber", "9999"))))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @DisplayName("BRAND — 사고 결제하고 받는다")
    class BrandJourney {

        /**
         * 브랜드 여정 전체. 마지막의 수령 확인이 <b>소유권이 실제로 옮겨가는 지점</b>이다.
         */
        @Test
        void walksFromPurchaseToOwnership() throws Exception {
            long materialId = registerMaterial(supplier, "브랜드가 살 데님", 30L);
            long listingId = createListing(supplier, materialId, 30L, 4_000L);

            mockMvc.perform(get("/api/v1/listings").param("page", "0").param("size", "20")
                            .header(HttpHeaders.AUTHORIZATION, brand))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(1));

            // 부분 구매. 금액은 서버가 계산한다 — 요청에 총액이 없다.
            long tradeId = createTrade(brand, listingId, 10L);
            mockMvc.perform(get("/api/v1/trades/{id}", tradeId).header(HttpHeaders.AUTHORIZATION, brand))
                    .andExpect(jsonPath("$.data.totalPrice").value(40_000));

            payFor(brand, tradeId);
            ship(supplier, tradeId);

            mockMvc.perform(post("/api/v1/trades/{id}/receive/confirm", tradeId)
                            .header(HttpHeaders.AUTHORIZATION, brand))
                    .andExpect(status().isOk());

            // 소유권 이전의 증거 — 산 만큼이 구매자 보유로 잡혀야 한다.
            mockMvc.perform(get("/api/materials/me").header(HttpHeaders.AUTHORIZATION, brand))
                    .andExpect(jsonPath("$.data[0].materialId").value(materialId))
                    .andExpect(jsonPath("$.data[0].available").value(10_000));
        }

        /** 결제를 취소하면 그 수량이 다시 팔린다. 빠뜨리면 이탈한 사용자마다 수량이 사라진다. */
        @Test
        void cancellingReturnsTheQuantityToTheListing() throws Exception {
            long materialId = registerMaterial(supplier, "취소될 데님", 30L);
            long listingId = createListing(supplier, materialId, 30L, 4_000L);
            long tradeId = createTrade(brand, listingId, 10L);

            mockMvc.perform(get("/api/v1/listings/{id}", listingId).header(HttpHeaders.AUTHORIZATION, brand))
                    .andExpect(jsonPath("$.data.availableQuantity").value(20_000));

            mockMvc.perform(post("/api/v1/trades/{id}/cancel", tradeId)
                            .header(HttpHeaders.AUTHORIZATION, brand))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/listings/{id}", listingId).header(HttpHeaders.AUTHORIZATION, brand))
                    .andExpect(jsonPath("$.data.availableQuantity").value(30_000));
        }

        /** 브랜드는 소재를 등록할 수는 있어도 <b>판매</b>는 못 한다는 것이 정책이다. */
        @Test
        void cannotSell() throws Exception {
            long materialId = registerMaterial(brand, "브랜드 소재", 10L);

            mockMvc.perform(post("/api/v1/listings")
                            .header(HttpHeaders.AUTHORIZATION, brand)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("materialId", materialId, "quantity", 5,
                                    "unit", "KG", "pricePerKg", 4_000))))
                    .andExpect(status().isForbidden());
        }

        /** 자기 판매글은 살 수 없다. */
        @Test
        void cannotBuyFromItself() throws Exception {
            long materialId = registerMaterial(supplier, "자가구매 시도", 30L);
            long listingId = createListing(supplier, materialId, 30L, 4_000L);

            mockMvc.perform(post("/api/v1/trades")
                            .header(HttpHeaders.AUTHORIZATION, supplier)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(tradeBody(listingId, 10L))))
                    .andExpect(status().is4xxClientError());
        }

        /**
         * <b>가공 의뢰를 아예 보낼 수 없다.</b>
         *
         * <p>{@code ProcessingRequestService#create} 가 가공업체의 등록된 역량
         * ({@code processor_capability}) 과 소재 종류·가공 목적·수량을 대조하는데,
         * <b>역량을 등록하는 API 도 코드도 없다.</b> {@code ProcessorCapability} 는 공개
         * 생성자조차 없어 자바에서 만들 방법이 없고, 저장하는 곳도 main 전체에 하나도 없다.
         * 그래서 조회 결과가 항상 비어 있고 {@code containsAll} 이 실패한다.
         *
         * <p>즉 브랜드 여정은 "요청까지는 된다" 가 아니라 <b>요청 자체가 막혀 있다.</b>
         * 역량 등록 경로가 생기면 이 테스트는 실패하고, 그때 위 여정으로 바꿔 쓰면 된다 —
         * 실패하는 것이 곧 기능이 붙었다는 신호가 된다.
         */
        @Test
        void cannotRequestProcessingBecauseCapabilitiesCannotBeRegistered() throws Exception {
            long materialId = registerMaterial(brand, "가공 맡길 소재", 20L);

            mockMvc.perform(post("/api/v1/processing-requests")
                            .header(HttpHeaders.AUTHORIZATION, brand)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("materialId", materialId, "processorId", processorId,
                                    "quantity", 10_000, "processTypes", List.of("CLEANING")))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("PROCESSING_CAPABILITY_MISMATCH"));
        }
    }

    @Nested
    @DisplayName("PROCESSOR — 들어온 의뢰를 보기만 한다")
    class ProcessorJourney {

        /**
         * 가공업체가 할 수 있는 것은 <b>빈 목록 조회뿐</b>이다.
         *
         * <p>목록 API 는 동작하지만 <b>들어올 요청이 없다</b> — 브랜드가 의뢰를 보내려면
         * 이 업체의 역량이 등록되어 있어야 하는데 그 경로가 없기 때문이다
         * ({@code cannotRequestProcessingBecauseCapabilitiesCannotBeRegistered} 참고).
         *
         * <p>설령 요청이 들어와도 <b>수락·견적 제출 API 가 없다.</b>
         * {@code processing_quote}·{@code processing_job} 은 스키마에만 있고 엔티티가 없어,
         * 가공업체는 의뢰를 보기만 할 뿐 답할 수 없다.
         */
        @Test
        void canOnlyReadAnEmptyRequestList() throws Exception {
            mockMvc.perform(get("/api/v1/processing-requests")
                            .header(HttpHeaders.AUTHORIZATION, processor))
                    .andExpect(status().isOk());
        }

        /** 가공업체는 소재를 등록할 수 없다 — 산출물은 가공 확정의 결과지 등록 대상이 아니다. */
        @Test
        void cannotRegisterMaterial() throws Exception {
            mockMvc.perform(post("/api/materials")
                            .header(HttpHeaders.AUTHORIZATION, processor)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(materialBody("가공업체 소재", 10L))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("MATERIAL_ROLE_NOT_ALLOWED"));
        }
    }

    @Nested
    @DisplayName("CONSUMER — 여정이 없다")
    class ConsumerJourney {

        /**
         * <b>소비자가 할 수 있는 일이 하나도 없다.</b>
         *
         * <p>제품 목록·상세·구매와 QR 이력 조회 API 가 전부 없다 ({@code product}·
         * {@code traceability} 도메인에 컨트롤러가 없다). 화면({@code /mypage})은 있지만
         * {@code createConsumerMyPageMock} 이 만든 가짜 데이터를 보여준다.
         *
         * <p>지갑도 가질 수 없고({@code canOwnWallet} 이 사업자 3역할로 제한) 이메일 로그인
         * API 도 없어서, <b>지금은 소비자로 로그인할 방법 자체가 없다.</b> 이 테스트가 토큰을
         * 직접 발급해 쓰는 이유다.
         *
         * <p>그래서 여기서 고정할 수 있는 것은 "소재 도메인에 들어올 수 없다" 는 경계뿐이다.
         * 제품 API 가 생기면 그때 이 클래스에 진짜 여정이 들어온다.
         */
        @Test
        void cannotEnterTheMaterialDomain() throws Exception {
            mockMvc.perform(post("/api/materials")
                            .header(HttpHeaders.AUTHORIZATION, consumer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(materialBody("소비자 소재", 10L))))
                    .andExpect(status().isForbidden());
        }

        /** 소비자는 소재를 살 수도 없다. 구매는 BRAND 만이다. */
        @Test
        void cannotBuyMaterial() throws Exception {
            long materialId = registerMaterial(supplier, "소비자가 못 살 소재", 30L);
            long listingId = createListing(supplier, materialId, 30L, 4_000L);

            mockMvc.perform(post("/api/v1/trades")
                            .header(HttpHeaders.AUTHORIZATION, consumer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(tradeBody(listingId, 10L))))
                    .andExpect(status().is4xxClientError());
        }
    }

    // ── 여정의 각 단계 ──────────────────────────────────────
    // 헬퍼가 응답에서 id 를 꺼내 다음 단계로 넘긴다. 테스트가 id 를 미리 알지 못하게 하는 것이
    // 의도적이다 — 알고 있으면 그 값을 서버가 만들었는지 확인하지 않게 된다.

    private long registerMaterial(String token, String name, long kg) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/materials")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(materialBody(name, kg))))
                .andExpect(status().isOk())
                .andReturn();
        return data(result).get("materialId").asLong();
    }

    private long createListing(String token, long materialId, long kg, long pricePerKg) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("materialId", materialId, "quantity", kg,
                                "unit", "KG", "pricePerKg", pricePerKg))))
                .andExpect(status().isOk())
                .andReturn();
        return data(result).get("listingId").asLong();
    }

    private long createTrade(String token, long listingId, long kg) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/trades")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(tradeBody(listingId, kg))))
                .andExpect(status().isOk())
                .andReturn();
        return data(result).get("tradeId").asLong();
    }

    /**
     * 결제 준비 → 승인.
     *
     * <p>테스트에는 카카오페이 키가 없어 {@code MockPaymentGateway} 가 붙는다. 그래도 금액
     * 대조·상태 전이·수량 처리는 실연동과 같은 코드를 지난다 — Mock 에서만 통과하는 경로가
     * 생기지 않게 한 설계다.
     */
    private void payFor(String token, long tradeId) throws Exception {
        mockMvc.perform(post("/api/v1/trades/{id}/payment/ready", tradeId)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/trades/{id}/payment/approve", tradeId)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("pgToken", "journey-pg-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));
    }

    private void ship(String token, long tradeId) throws Exception {
        mockMvc.perform(patch("/api/v1/trades/{id}/ship", tradeId)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("carrier", "CJ대한통운", "trackingNumber", "1234567890"))))
                .andExpect(status().isOk());
    }

    // ── 거들기 ──────────────────────────────────────────────

    private Map<String, Object> materialBody(String name, long kg) {
        return Map.of("materialType", "DENIM", "rootOrigin", "WASTE", "name", name,
                "grade", "A", "quantity", kg, "unit", "KG");
    }

    private Map<String, Object> tradeBody(long listingId, long kg) {
        return Map.of("listingId", listingId, "quantity", kg, "unit", "KG",
                "paymentMethod", "KAKAO_PAY", "receiverName", "받는이",
                "receiverPhone", "010-1111-2222", "zipCode", "12345", "address", "부산시 강서구");
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    /**
     * 사업자 가입 → 로그인 → 지갑 연결. 화면의 순서 그대로다.
     *
     * <p><b>가입 응답에 토큰이 없다.</b> 가입과 로그인이 나뉘어 있어 화면도 두 번 부른다.
     * 그래서 여기서도 로그인을 따로 한다 — 붙여 두면 그 계약이 바뀌어도 드러나지 않는다.
     */
    private String signUpBusinessAndLogin(String email, UserRole role, String walletAddress)
            throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup/business")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(businessSignupBody(email, role))))
                .andExpect(status().isOk());

        String token = login(email);
        // 지갑이 있어야 소재를 소유할 수 있다. 없으면 등록이 MATERIAL_WALLET_NOT_LINKED 로 막힌다.
        Long userId = userRepository.findByEmail(email).orElseThrow().getId();
        walletRepository.save(UserWallet.link(userId, walletAddress, SSAFY_CHAIN_ID));
        return token;
    }

    /** 소비자 가입 → 로그인. <b>지갑을 붙이지 않는다</b> — canOwnWallet 이 사업자 3역할뿐이다. */
    private String signUpConsumerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup/consumer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "소비자", "email", email,
                                "phone", "010-0000-0009", "password", PASSWORD))))
                .andExpect(status().isOk());
        return login(email);
    }

    /** 이메일 로그인. 응답의 accessToken 을 그대로 쓴다 — 토큰을 직접 발급하지 않는다. */
    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + data(result).get("accessToken").asText();
    }

    private Map<String, Object> businessSignupBody(String email, UserRole role) {
        return Map.ofEntries(
                Map.entry("role", role.name()),
                Map.entry("companyName", "저니컴퍼니"),
                Map.entry("businessNumber", "123-45-" + Math.abs(email.hashCode() % 100000)),
                Map.entry("representativeName", "대표자"),
                Map.entry("companyPhone", "051-000-0000"),
                Map.entry("zipCode", "12345"),
                Map.entry("address", "부산시 강서구"),
                Map.entry("addressDetail", "1층"),
                Map.entry("email", email),
                Map.entry("password", PASSWORD));
    }

    /** FK 역순으로 지운다. */
    private void clean() {
        companyProfileRepository.deleteAll();
        processingInputRepository.deleteAll();
        processingRequestRepository.deleteAll();
        shippingRepository.deleteAll();
        tradeRepository.deleteAll();
        listingRepository.deleteAll();
        lockRepository.deleteAll();
        balanceRepository.deleteAll();
        materialRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }
}
