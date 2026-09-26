package com.ssafy.upcycle.trade.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialEventType;
import com.ssafy.upcycle.material.entity.MaterialLock;
import com.ssafy.upcycle.material.entity.MaterialLockStatus;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialEventRepository;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.material.service.MaterialEventRecorder;
import com.ssafy.upcycle.notification.entity.NotificationType;
import com.ssafy.upcycle.notification.repository.UserNotificationRepository;
import com.ssafy.upcycle.trade.entity.ListingStatus;
import com.ssafy.upcycle.trade.entity.MaterialListing;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import com.ssafy.upcycle.trade.payment.KakaoPayProperties;
import com.ssafy.upcycle.trade.payment.PaymentApproval;
import com.ssafy.upcycle.trade.payment.PaymentApproveCommand;
import com.ssafy.upcycle.trade.payment.PaymentCancelCommand;
import com.ssafy.upcycle.trade.payment.PaymentGateway;
import com.ssafy.upcycle.trade.payment.PaymentReady;
import com.ssafy.upcycle.trade.payment.PaymentReadyCommand;
import com.ssafy.upcycle.trade.repository.ListingLockRepository;
import com.ssafy.upcycle.trade.repository.MaterialListingRepository;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
import com.ssafy.upcycle.trade.repository.TradeShippingRepository;
import com.ssafy.upcycle.trade.service.PaymentService;
import com.ssafy.upcycle.trade.service.TradeResponseAssembler;
import com.ssafy.upcycle.trade.service.TradeNotificationService;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Supplier Market · Brand Market · 결제의 엔드투엔드 계약.
 *
 * <p>서비스를 따로 단위 테스트하지 않고 HTTP 로 관통시킨다. 이 도메인에서 실제로 깨지는 것은
 * 계산식이 아니라 <b>여러 테이블에 걸친 순서</b>이기 때문이다 — 잠금이 먼저 저장되는가,
 * 취소가 수량을 되돌리는가, 결제 금액이 대조되는가. 서비스를 모킹하면 그 순서가 검증에서 빠진다.
 * 순수 계산은 {@code TradePricingTests} 가 따로 본다.
 *
 * <p>{@code @SpringBootTest} 는 트랜잭션 롤백이 없어 커밋한 행이 같은 in-memory H2 에 남는다.
 * 전체 스위트에서만 깨지는 실패를 만들지 않으려고 앞뒤 양쪽에서 지운다
 * ({@code MaterialControllerTests} 와 같은 이유).
 */
@SpringBootTest
@AutoConfigureMockMvc
class MarketplaceApiTests {

    private static final String SELLER_WALLET = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String BUYER_WALLET = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final long SSAFY_CHAIN_ID = 31221L;

    /** 30kg 보유. 부분 구매를 눈으로 따라가기 쉬운 값이다. */
    private static final long OWNED_QUANTITY = 30_000L;

    private static final long PRICE_PER_KG = 14_000L;

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
    private MaterialRepository materialRepository;

    @Autowired
    private MaterialBalanceRepository balanceRepository;

    @Autowired
    private MaterialLockRepository lockRepository;

    @Autowired
    private MaterialListingRepository listingRepository;

    @Autowired
    private MaterialTradeRepository tradeRepository;

    @Autowired
    private TradeShippingRepository shippingRepository;

    @Autowired
    private ListingLockRepository listingLockRepository;

    @Autowired
    private TradeResponseAssembler tradeResponseAssembler;

    @Autowired
    private KakaoPayProperties kakaoPayProperties;

    @Autowired
    private Clock clock;

    @Autowired
    private MaterialEventRecorder materialEventRecorder;

    @Autowired
    private MaterialEventRepository materialEventRepository;

    @Autowired
    private UserNotificationRepository notificationRepository;

    @Autowired
    private TradeNotificationService tradeNotificationService;

    private User seller;
    private User buyer;
    private Material material;
    private Material cotton;
    private Material silk;
    private String sellerToken;
    private String buyerToken;

    @BeforeEach
    void setUp() {
        clean();
        seller = userRepository.save(User.register(
                "market-seller@example.com", "{noop}x", "공급자", "010-0000-0000", UserRole.SUPPLIER));
        buyer = userRepository.save(User.register(
                "market-buyer@example.com", "{noop}x", "브랜드", "010-1111-1111", UserRole.BRAND));
        walletRepository.save(UserWallet.link(seller.getId(), SELLER_WALLET, SSAFY_CHAIN_ID));
        walletRepository.save(UserWallet.link(buyer.getId(), BUYER_WALLET, SSAFY_CHAIN_ID));

        material = materialRepository.save(Material.register(
                "MAT-900001",
                MaterialType.DENIM,
                MaterialRootOrigin.WASTE,
                "폐데님 원단",
                "수거된 청바지",
                "A",
                OWNED_QUANTITY,
                null,
                null,
                seller.getId()));
        balanceRepository.save(MaterialBalance.of(
                material.getId(), seller.getId(), SELLER_WALLET, OWNED_QUANTITY));

        // 필터 조합을 시험할 소재 두 개를 더 둔다. 종류·등급·가격을 데님과 다르게 잡아
        // "여러 개 중 하나만 남는" 것을 볼 수 있게 한다 — 판매글이 하나뿐이면 필터가
        // 걸렀는지 애초에 아무것도 없었는지 구분되지 않는다.
        cotton = materialRepository.save(Material.register(
                "MAT-900002",
                MaterialType.COTTON,
                MaterialRootOrigin.WASTE,
                "폐면 자투리",
                "봉제 공장 재단 잔여",
                "B",
                OWNED_QUANTITY,
                null,
                null,
                seller.getId()));
        balanceRepository.save(MaterialBalance.of(
                cotton.getId(), seller.getId(), SELLER_WALLET, OWNED_QUANTITY));

        silk = materialRepository.save(Material.register(
                "MAT-900003",
                MaterialType.SILK,
                MaterialRootOrigin.VIRGIN,
                "미사용 실크",
                "재고 소진분",
                "A",
                OWNED_QUANTITY,
                null,
                null,
                seller.getId()));
        balanceRepository.save(MaterialBalance.of(
                silk.getId(), seller.getId(), SELLER_WALLET, OWNED_QUANTITY));

        sellerToken = tokenFor(seller);
        buyerToken = tokenFor(buyer);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    // ── 인증·권한 ────────────────────────────────────────────

    @Test
    void requiresAuthentication() throws Exception {
        // SecurityConfig 의 permitAll 목록에 /api/listings 가 들어가면 이 테스트가 깨진다.
        mockMvc.perform(get("/api/v1/listings")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAConsumerFromListing() throws Exception {
        User consumer = userRepository.save(User.register(
                "market-consumer@example.com", "{noop}x", "소비자", "010-2222-2222", UserRole.CONSUMER));

        mockMvc.perform(post("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(consumer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(listingRequest(OWNED_QUANTITY))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LISTING_ROLE_NOT_ALLOWED"));
    }

    @Test
    void rejectsASupplierFromBuying() throws Exception {
        // 온체인 createOrder 가 BRAND_ROLE 을 요구한다. 여기서 열어 주면
        // DB 거래는 만들어졌는데 체인 주문만 실패하는 거래가 생긴다.
        Long listingId = registerListing(OWNED_QUANTITY);
        User otherSupplier = userRepository.save(User.register(
                "market-supplier2@example.com", "{noop}x", "공급자2", "010-3333-3333", UserRole.SUPPLIER));
        walletRepository.save(UserWallet.link(
                otherSupplier.getId(), "0xcccccccccccccccccccccccccccccccccccccccc", SSAFY_CHAIN_ID));

        mockMvc.perform(post("/api/v1/trades")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(otherSupplier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(tradeRequest(listingId, 1_000L))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TRADE_ROLE_NOT_ALLOWED"));
    }

    // ── 판매 등록 ────────────────────────────────────────────

    @Test
    void registersAListingAndLocksTheQuantity() throws Exception {
        Long listingId = registerListing(20_000L);

        MaterialListing listing = listingRepository.findById(listingId).orElseThrow();
        assertThat(listing.getAvailableQuantity()).isEqualTo(20_000L);
        assertThat(listing.getStatus()).isEqualTo(ListingStatus.ON_SALE);

        // 잠금이 판매 글을 가리켜야 한다. 자리값(0)이 살아남으면 이 단언이 잡는다.
        MaterialLock lock = lockRepository.findById(listing.getMaterialLockId()).orElseThrow();
        assertThat(lock.getReferenceId()).isEqualTo(listingId);
        assertThat(lock.getAmount()).isEqualTo(20_000L);
        assertThat(lock.getStatus()).isEqualTo(MaterialLockStatus.ACTIVE);
    }

    @Test
    void leavesChainListingIdNullUntilTheContractCall() throws Exception {
        // 등록은 DB 만 채운다. null 이 "등록됐지만 온체인에 없음" 의 유일한 신호다.
        Long listingId = registerListing(20_000L);

        assertThat(listingRepository.findById(listingId).orElseThrow().getChainListingId()).isNull();
    }

    @Test
    void rejectsListingMoreThanIsOwned() throws Exception {
        mockMvc.perform(post("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(listingRequest(OWNED_QUANTITY + 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_QUANTITY_EXCEEDS_AVAILABLE"));
    }

    @Test
    void countsExistingLocksAgainstAvailableQuantity() throws Exception {
        // 보유량만 보면 같은 30kg 을 여러 판매 글에 걸 수 있다. 이 테스트가 그것을 막는다.
        registerListing(20_000L);

        mockMvc.perform(post("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(listingRequest(15_000L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_QUANTITY_EXCEEDS_AVAILABLE"));
    }

    @Test
    void rejectsListingAMaterialTheSellerDoesNotHold() throws Exception {
        // setUp 이 900001~900003 을 쓴다. 개별 테스트가 만드는 소재는 900009 부터 —
        // material_code 가 UNIQUE 라 겹치면 테스트가 서로를 깨뜨린다.
        Material other = materialRepository.save(Material.register(
                "MAT-900009",
                MaterialType.COTTON,
                MaterialRootOrigin.VIRGIN,
                "남의 소재",
                null,
                null,
                10_000L,
                null,
                null,
                buyer.getId()));

        Map<String, Object> request = listingRequest(1_000L);
        request.put("materialId", other.getId());

        mockMvc.perform(post("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LISTING_MATERIAL_NOT_OWNED"));
    }

    @Test
    void convertsKilogramsToGrams() throws Exception {
        Map<String, Object> request = listingRequest(20L);
        request.put("unit", "KG");

        Long listingId = createdListingId(request);

        assertThat(listingRepository.findById(listingId).orElseThrow().getTotalQuantity())
                .isEqualTo(20_000L);
    }

    @Test
    void rejectsAFractionalQuantity() throws Exception {
        // 12.5kg 이 12kg 으로 조용히 버려지면 500g 이 요청 성공과 함께 사라진다.
        Map<String, Object> request = listingRequest(0L);
        request.put("quantity", 12.5);
        request.put("unit", "KG");

        mockMvc.perform(post("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(request)))
                .andExpect(status().isBadRequest());
    }

    // ── 목록·검색·필터 ───────────────────────────────────────

    @Test
    void listsOnSaleListingsWithMaterialInformation() throws Exception {
        registerListing(20_000L);

        mockMvc.perform(get("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].materialName").value("폐데님 원단"))
                .andExpect(jsonPath("$.data.content[0].materialType").value("DENIM"))
                .andExpect(jsonPath("$.data.content[0].pricePerKg").value(PRICE_PER_KG))
                .andExpect(jsonPath("$.data.content[0].sellerName").value("공급자"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void filtersByMaterialType() throws Exception {
        registerListing(20_000L);

        mockMvc.perform(get("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .param("materialType", "COTTON"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void filtersByPriceRange() throws Exception {
        registerListing(20_000L);

        mockMvc.perform(get("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .param("maxPricePerKg", String.valueOf(PRICE_PER_KG - 1)))
                .andExpect(jsonPath("$.data.content.length()").value(0));

        mockMvc.perform(get("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .param("minPricePerKg", String.valueOf(PRICE_PER_KG)))
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    @Test
    void searchesByMaterialName() throws Exception {
        registerListing(20_000L);

        mockMvc.perform(get("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .param("keyword", "데님"))
                .andExpect(jsonPath("$.data.content.length()").value(1));

        mockMvc.perform(get("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .param("keyword", "면"))
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void filtersToOneSellerForTheSupplierMarket() throws Exception {
        // Supplier Market 은 sellerId 필터 하나로 같은 엔드포인트를 쓴다.
        registerListing(20_000L);

        mockMvc.perform(get("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .param("sellerId", String.valueOf(seller.getId())))
                .andExpect(jsonPath("$.data.content.length()").value(1));

        mockMvc.perform(get("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .param("sellerId", String.valueOf(buyer.getId())))
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void filtersByMaterialId() throws Exception {
        // 소재 상세 화면이 "이 소재가 지금 판매 중인가" 를 묻는 경로다 (-438).
        registerListing(20_000L);

        mockMvc.perform(get("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .param("materialId", String.valueOf(material.getId())))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].materialId").value(material.getId()));

        // 판매 글이 없는 소재는 빈 목록이다 — 완료 조건이 명시한 값이다.
        mockMvc.perform(get("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .param("materialId", String.valueOf(cotton.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void capsThePageSize() throws Exception {
        // size=100000 한 번으로 전체 테이블을 끌어오지 못해야 한다.
        registerListing(20_000L);

        mockMvc.perform(get("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .param("size", "100000"))
                .andExpect(jsonPath("$.data.size").value(100));
    }

    // ── 필터 조합 (-302) ─────────────────────────────────────
    //
    // 위 테스트들은 필터를 하나씩만 건다. 조건이 전부 AND 라 구조적으로는 조합도 동작하지만,
    // 누가 조건 하나를 OR 로 바꾸거나 파라미터 이름을 잘못 적으면 **단일 필터 테스트는 모두
    // 통과하고 조합만 깨진다.** 그래서 교집합이 실제로 나오는지 따로 고정한다.
    //
    // 판매글 세 개를 서로 다른 조건으로 등록한다.
    //   데님 / A / 14,000원
    //   폐면 / B /  4,200원
    //   가죽 / A / 52,000원

    @Test
    void listsEveryOnSaleListingWithoutFilters() throws Exception {
        // 기준선. 이것이 없으면 아래 조합 테스트가 0 건을 반환해도 "잘 걸러졌다" 인지
        // "애초에 데이터가 없었다" 인지 알 수 없다.
        registerThreeListings();

        mockMvc.perform(get("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(3))
                .andExpect(jsonPath("$.data.totalElements").value(3));
    }

    /**
     * 생성 유형은 <b>출처와 독립된 축</b>이다 (화면 설계서 B03).
     *
     * <p>세 소재 모두 {@code INITIAL} 이다 — {@code Material.register} 가 최초 등록으로
     * 고정하기 때문이다. 그래서 {@code INITIAL} 로 걸면 전부, {@code PROCESSED} 로 걸면
     * 하나도 남지 않아야 한다. 값을 무시하고 있으면 두 번째 기대가 깨진다.
     */
    @Test
    void filtersByGenerationType() throws Exception {
        registerThreeListings();

        mockMvc.perform(get("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .param("generationType", "INITIAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(3));

        mockMvc.perform(get("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .param("generationType", "PROCESSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    /** 두 축을 함께 걸면 교집합이다. 한쪽이 무시되면 개수가 달라진다. */
    @Test
    void combinesRootOriginAndGenerationType() throws Exception {
        registerThreeListings();

        // 폐기물이면서 최초 등록 — 데님·폐면 둘. 가죽은 VIRGIN 이라 빠진다.
        mockMvc.perform(get("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .param("rootOrigin", "WASTE")
                        .param("generationType", "INITIAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    /** 목록 응답이 생성 유형을 함께 내려준다 — 화면이 배지로 그린다. */
    @Test
    void exposesGenerationTypeInTheResponse() throws Exception {
        registerListing(10_000L);

        mockMvc.perform(get("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].generationType").value("INITIAL"));
    }

    @Test
    void combinesMaterialTypeAndGrade() throws Exception {
        registerThreeListings();

        // 등급 A 는 데님·가죽 둘인데 종류까지 걸면 데님만 남는다.
        mockMvc.perform(get("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .param("materialType", "DENIM")
                        .param("grade", "A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].materialName").value("폐데님 원단"));
    }

    @Test
    void combinesGradeAndPriceCeiling() throws Exception {
        registerThreeListings();

        // 등급 A 둘 중 가죽(52,000원)이 상한에 걸려 떨어진다.
        mockMvc.perform(get("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .param("grade", "A")
                        .param("maxPricePerKg", "20000"))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].materialName").value("폐데님 원단"));
    }

    @Test
    void combinesKeywordAndOrigin() throws Exception {
        registerThreeListings();

        // 이름에 "폐" 가 들어가는 것은 데님·폐면 둘. 출처가 WASTE 인 것도 그 둘이라
        // 교집합은 2 건이다 — 조건이 서로를 잘라내지 않는 경우도 확인한다.
        mockMvc.perform(get("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .param("keyword", "폐")
                        .param("rootOrigin", "WASTE"))
                .andExpect(jsonPath("$.data.content.length()").value(2));

        // 출처를 VIRGIN 으로 바꾸면 교집합이 비어야 한다 — 가죽은 이름에 "폐" 가 없다.
        mockMvc.perform(get("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .param("keyword", "폐")
                        .param("rootOrigin", "VIRGIN"))
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void combinesThreeFiltersAtOnce() throws Exception {
        registerThreeListings();

        mockMvc.perform(get("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .param("materialType", "COTTON")
                        .param("rootOrigin", "WASTE")
                        .param("grade", "B")
                        .param("maxPricePerKg", "5000"))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].materialName").value("폐면 자투리"));
    }

    @Test
    void keepsTotalCountConsistentWithTheFilteredList() throws Exception {
        // 목록과 총 개수가 **같은 조건**을 쓰는지 확인한다. JPQL 시절에는 where 절이
        // value 와 countQuery 에 두 벌로 적혀 있어서, 한쪽만 고치면 "총 3건" 이라고
        // 표시하면서 2건만 보여주는 상태가 될 수 있었다.
        registerThreeListings();

        mockMvc.perform(get("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .param("grade", "A")
                        // size 를 1 로 줄여 count 쿼리가 실제로 나가게 만든다. 결과가 한
                        // 페이지에 다 들어가면 PageableExecutionUtils 가 count 를 생략한다.
                        .param("size", "1"))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    // ── 금액 계산·부분 구매 ──────────────────────────────────

    @Test
    void quotesThePriceWithoutChangingAnything() throws Exception {
        Long listingId = registerListing(20_000L);

        mockMvc.perform(get("/api/v1/trades/quote")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .param("listingId", String.valueOf(listingId))
                        .param("quantity", "3")
                        .param("unit", "KG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPrice").value(42_000))
                .andExpect(jsonPath("$.data.purchasable").value(true));

        assertThat(listingRepository.findById(listingId).orElseThrow().getAvailableQuantity())
                .isEqualTo(20_000L);
    }

    @Test
    void marksAnOversizedQuoteAsNotPurchasable() throws Exception {
        Long listingId = registerListing(20_000L);

        mockMvc.perform(get("/api/v1/trades/quote")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .param("listingId", String.valueOf(listingId))
                        .param("quantity", "25")
                        .param("unit", "KG"))
                .andExpect(jsonPath("$.data.purchasable").value(false));
    }

    /**
     * 구매가 배송 행을 함께 만들지 않으면 발송·수령이 둘 다 404 로 막힌다. 프론트에서
     * 실제로 보고된 블로커이고, 우회할 방법이 없어 거래 흐름 전체가 멈춘다.
     */
    @Test
    void purchaseCreatesTheShippingAddress() throws Exception {
        Long listingId = registerListing(20_000L);

        String response = purchase(listingId, 8_000L);
        Long tradeId = jsonLong(response, "tradeId");

        assertThat(shippingRepository.findByTradeId(tradeId)).isPresent();
        assertThat(objectMapper.readTree(response).path("data").path("shipping").path("address")
                .asText()).isEqualTo("서울시 성동구");
    }

    @Test
    void purchaseWithoutAShippingAddressIsRejected() throws Exception {
        Long listingId = registerListing(20_000L);
        Map<String, Object> request = tradeRequest(listingId, 8_000L);
        request.remove("address");

        mockMvc.perform(post("/api/v1/trades")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    /** 발송 응답으로 한 번 받는 것만으로는 새로고침하면 송장이 사라진다. */
    @Test
    void tradeDetailKeepsTheWaybillAfterShipping() throws Exception {
        Long listingId = registerListing(20_000L);
        Long tradeId = jsonLong(purchase(listingId, 8_000L), "tradeId");
        payFor(tradeId);

        mockMvc.perform(patch("/api/v1/trades/" + tradeId + "/ship")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"carrier\": \"CJ대한통운\", \"trackingNumber\": \"123456789012\" }"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/trades/" + tradeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shipping.carrier").value("CJ대한통운"))
                .andExpect(jsonPath("$.data.shipping.trackingNumber").value("123456789012"))
                .andExpect(jsonPath("$.data.shipping.shippedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.shipping.deliveredAt").doesNotExist());
    }

    /** 판매자는 발송 화면에서 어디로 보낼지를 봐야 한다. */
    @Test
    void tradeListCarriesTheShippingAddress() throws Exception {
        Long listingId = registerListing(20_000L);
        purchase(listingId, 8_000L);

        mockMvc.perform(get("/api/v1/trades")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .param("side", "SELL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].shipping.receiverName").value("김구매"))
                .andExpect(jsonPath("$.data.content[0].shipping.zipCode").value("04766"));
    }

    @Test
    void buysPartOfAListingAndReducesTheRemainder() throws Exception {
        Long listingId = registerListing(20_000L);

        String response = purchase(listingId, 8_000L);

        assertThat(jsonLong(response, "totalPrice")).isEqualTo(112_000L);
        assertThat(listingRepository.findById(listingId).orElseThrow().getAvailableQuantity())
                .isEqualTo(12_000L);
        assertThat(listingRepository.findById(listingId).orElseThrow().getStatus())
                .isEqualTo(ListingStatus.ON_SALE);
    }

    @Test
    void closesTheListingWhenTheLastGramIsSold() throws Exception {
        Long listingId = registerListing(20_000L);

        purchase(listingId, 20_000L);

        assertThat(listingRepository.findById(listingId).orElseThrow().getStatus())
                .isEqualTo(ListingStatus.SOLD_OUT);
    }

    @Test
    void rejectsBuyingMoreThanRemains() throws Exception {
        Long listingId = registerListing(20_000L);
        purchase(listingId, 15_000L);

        mockMvc.perform(post("/api/v1/trades")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(tradeRequest(listingId, 6_000L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_QUANTITY_EXCEEDS_AVAILABLE"));
    }

    @Test
    void rejectsABrandFromListing() throws Exception {
        // BRAND 는 소재를 사서 제품을 만드는 쪽이라 판매자가 되지 않는다.
        // 소재를 등록할 수는 있으므로(canRegisterMaterial) 등록 권한과 판매 권한이 다르다는 것이
        // 이 테스트의 요지다.
        //
        // 발송 처리도 hasRole('SUPPLIER') 다. 여기를 넓히면 팔리고 결제까지 됐는데 발송이
        // 막히는 거래가 생기므로 두 목록은 항상 같아야 한다.
        balanceRepository.save(MaterialBalance.of(
                material.getId(), buyer.getId(), BUYER_WALLET, 5_000L));

        mockMvc.perform(post("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(listingRequest(1_000L))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LISTING_ROLE_NOT_ALLOWED"));
    }

    // 자기 글 구매(TRADE_SELF_PURCHASE)는 HTTP 로 만들 수 없다 — 판매자는 SUPPLIER, 구매자는
    // BRAND 라 두 집합이 겹치지 않는다. 서비스의 검사는 역할 정책이 바뀔 때를 대비한
    // 방어로 남아 있고, 그 자체를 여기서 재현할 수는 없다.


    @Test
    void snapshotsBothWalletAddresses() throws Exception {
        Long listingId = registerListing(20_000L);

        String response = purchase(listingId, 1_000L);

        assertThat(jsonText(response, "sellerWallet")).isEqualTo(SELLER_WALLET);
        assertThat(jsonText(response, "buyerWallet")).isEqualTo(BUYER_WALLET);
    }

    @Test
    void refusesToTradeWithoutALinkedWallet() throws Exception {
        // 지갑이 없으면 소유권 이전 대상이 정해지지 않은 거래가 된다.
        Long listingId = registerListing(20_000L);
        User walletless = userRepository.save(User.register(
                "market-nowallet@example.com", "{noop}x", "지갑없음", "010-5555-5555", UserRole.BRAND));

        mockMvc.perform(post("/api/v1/trades")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(walletless))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(tradeRequest(listingId, 1_000L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRADE_WALLET_NOT_LINKED"));
    }

    // ── 결제 ────────────────────────────────────────────────
    //
    // 카카오페이 키가 없는 환경이라 MockPaymentGateway 가 붙는다. 그래도 검증 대상은
    // 그대로다 — 금액 대조, 중복 승인 차단, 당사자 확인, 준비 없는 승인 차단은 전부
    // PaymentService 의 코드이고 실제 PG 에서도 같은 경로가 실행된다.

    @Test
    void preparesPaymentAndStoresTheTid() throws Exception {
        Long listingId = registerListing(20_000L);
        Long tradeId = jsonLong(purchase(listingId, 3_000L), "tradeId");

        String response = mockMvc.perform(post("/api/v1/trades/" + tradeId + "/payment/ready")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPrice").value(42_000))
                // 키가 없으므로 Mock 이다. 화면은 이 값으로 결제창 이동 여부를 정한다.
                .andExpect(jsonPath("$.data.live").value(false))
                .andReturn().getResponse().getContentAsString();

        assertThat(jsonText(response, "tid")).isNotBlank();

        // 준비만으로는 결제된 것이 아니다. tid 는 남지만 상태는 그대로여야 한다.
        MaterialTrade prepared = tradeRepository.findById(tradeId).orElseThrow();
        assertThat(prepared.getStatus()).isEqualTo(TradeStatus.CREATED);
        assertThat(prepared.getPaidAt()).isNull();
        assertThat(prepared.getPaymentReference()).isNotBlank();
    }

    @Test
    void approvesPaymentAfterReady() throws Exception {
        Long listingId = registerListing(20_000L);
        Long tradeId = jsonLong(purchase(listingId, 3_000L), "tradeId");
        readyPayment(tradeId);

        mockMvc.perform(post("/api/v1/trades/" + tradeId + "/payment/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(approveRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.paymentAmount").value(42_000))
                .andExpect(jsonPath("$.data.paidAt").isNotEmpty());
        assertThat(materialEventRepository.findAllByMaterialIdOrderByOccurredAtAscIdAsc(
                        material.getId(), PageRequest.of(0, 10)).getContent())
                .extracting(event -> event.getEventType())
                .containsExactly(
                        MaterialEventType.MARKETPLACE_LISTED,
                        MaterialEventType.TRADE_CREATED,
                        MaterialEventType.TRADE_PAID);
    }

    @Test
    void notifiesCounterpartiesAcrossTheTradeLifecycle() throws Exception {
        Long listingId = registerListing(20_000L);
        Long tradeId = jsonLong(purchase(listingId, 3_000L), "tradeId");
        payFor(tradeId);
        mockMvc.perform(patch("/api/v1/trades/" + tradeId + "/ship")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\":\"CJ대한통운\",\"trackingNumber\":\"123456789012\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/trades/" + tradeId + "/receive/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk());

        assertThat(notificationRepository
                .findAllByRecipientIdOrderByCreatedAtDescIdDesc(
                        seller.getId(), PageRequest.of(0, 20))
                .getContent())
                .extracting(notification -> notification.getType())
                .containsExactly(
                        NotificationType.TRADE_RECEIVED,
                        NotificationType.TRADE_PAID,
                        NotificationType.TRADE_CREATED);
        assertThat(notificationRepository
                .findAllByRecipientIdOrderByCreatedAtDescIdDesc(
                        buyer.getId(), PageRequest.of(0, 20))
                .getContent())
                .extracting(notification -> notification.getType())
                .containsExactly(NotificationType.TRADE_SHIPPED);
    }

    @Test
    void sellerCancellationNotifiesTheBuyer() throws Exception {
        Long listingId = registerListing(20_000L);
        Long tradeId = jsonLong(purchase(listingId, 3_000L), "tradeId");

        mockMvc.perform(post("/api/v1/trades/" + tradeId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk());

        assertThat(notificationRepository
                .findAllByRecipientIdOrderByCreatedAtDescIdDesc(
                        buyer.getId(), PageRequest.of(0, 20))
                .getContent())
                .extracting(notification -> notification.getType())
                .containsExactly(NotificationType.TRADE_CANCELED);
    }

    @Test
    void refusesToApproveWithoutReady() throws Exception {
        // 준비를 건너뛰고 승인을 부르면 서버가 쓸 tid 가 없다. 클라이언트가 tid 를 보내게
        // 두었다면 남의 결제 번호로 승인을 시도할 수 있었다.
        Long listingId = registerListing(20_000L);
        Long tradeId = jsonLong(purchase(listingId, 3_000L), "tradeId");

        mockMvc.perform(post("/api/v1/trades/" + tradeId + "/payment/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(approveRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_PREPARED"));
    }

    @Test
    void rejectsAnApprovalForTheWrongAmount() throws Exception {
        // PG 가 거래 금액과 다른 금액을 승인해 돌려주는 경우.
        // Mock 은 요청 금액을 그대로 돌려주므로 이 상황은 게이트웨이를 직접 흉내 내야 만들어진다.
        // 이 검사가 없으면 금액이 어긋난 승인이 정상 결제로 남는다.
        Long listingId = registerListing(20_000L);
        Long tradeId = jsonLong(purchase(listingId, 3_000L), "tradeId");
        readyPayment(tradeId);

        PaymentService service = new PaymentService(
                tradeRepository,
                listingLockRepository,
                materialRepository,
                tradeResponseAssembler,
                new WrongAmountGateway(),
                kakaoPayProperties,
                clock,
                materialEventRecorder,
                tradeNotificationService);

        assertThatThrownBy(() -> service.approve(buyer.getId(), tradeId, "TOKEN"))
                .isInstanceOf(BusinessException.class)
                .satisfies(thrown -> assertThat(
                        ((BusinessException) thrown).getErrorCode().code())
                        .isEqualTo("PAYMENT_AMOUNT_MISMATCH"));
    }

    @Test
    void rejectsADuplicatePayment() throws Exception {
        Long listingId = registerListing(20_000L);
        Long tradeId = jsonLong(purchase(listingId, 3_000L), "tradeId");
        payFor(tradeId);

        mockMvc.perform(post("/api/v1/trades/" + tradeId + "/payment/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(approveRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_STATE_INVALID"));
    }

    @Test
    void rejectsAPaymentFromSomeoneElse() throws Exception {
        Long listingId = registerListing(20_000L);
        Long tradeId = jsonLong(purchase(listingId, 3_000L), "tradeId");

        mockMvc.perform(post("/api/v1/trades/" + tradeId + "/payment/ready")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TRADE_NOT_PARTICIPANT"));
    }

    /** 승인 금액을 1원으로 돌려주는 가짜 PG. 금액 대조가 실제로 도는지 확인하는 데만 쓴다. */
    private static final class WrongAmountGateway implements PaymentGateway {

        @Override
        public PaymentReady ready(PaymentReadyCommand command) {
            return new PaymentReady("TID-WRONG", "");
        }

        @Override
        public PaymentApproval approve(PaymentApproveCommand command) {
            return new PaymentApproval(command.tid(), 1L, LocalDateTime.now());
        }

        @Override
        public void cancel(PaymentCancelCommand command) {
        }

        @Override
        public boolean isLive() {
            return true;
        }
    }

    // ── 취소 ────────────────────────────────────────────────

    @Test
    void cancellingATradeGivesTheQuantityBack() throws Exception {
        // 이것이 빠지면 결제창을 닫은 사용자마다 판매 수량이 영구히 사라진다.
        Long listingId = registerListing(20_000L);
        Long tradeId = jsonLong(purchase(listingId, 8_000L), "tradeId");

        mockMvc.perform(post("/api/v1/trades/" + tradeId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));

        assertThat(listingRepository.findById(listingId).orElseThrow().getAvailableQuantity())
                .isEqualTo(20_000L);
    }

    /** 상태만 남기면 "언제 왜 취소됐는가" 를 답할 수 없다. 분쟁·문의의 근거가 되는 값이다. */
    @Test
    void cancellationKeepsTheReasonAndTime() throws Exception {
        Long listingId = registerListing(20_000L);
        Long tradeId = jsonLong(purchase(listingId, 8_000L), "tradeId");

        mockMvc.perform(post("/api/v1/trades/" + tradeId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"reason\": \"재고 부족으로 발송이 어렵습니다\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelReason").value("재고 부족으로 발송이 어렵습니다"))
                .andExpect(jsonPath("$.data.canceledAt").isNotEmpty());

        MaterialTrade trade = tradeRepository.findById(tradeId).orElseThrow();
        assertThat(trade.getCancelReason()).isEqualTo("재고 부족으로 발송이 어렵습니다");
        assertThat(trade.getCanceledAt()).isNotNull();
    }

    /** 결제창을 닫고 나온 취소처럼 사유를 적을 자리가 없는 경로가 정상이다. */
    @Test
    void cancellationWithoutABodyStillRecordsTheTime() throws Exception {
        Long listingId = registerListing(20_000L);
        Long tradeId = jsonLong(purchase(listingId, 8_000L), "tradeId");

        mockMvc.perform(post("/api/v1/trades/" + tradeId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelReason").doesNotExist())
                .andExpect(jsonPath("$.data.canceledAt").isNotEmpty());
    }

    /** 폼의 빈 입력은 null 이 아니라 "" 로 온다. 그것을 사유로 저장하면 빈 값이 쌓인다. */
    @Test
    void blankReasonIsTreatedAsNoReason() throws Exception {
        Long listingId = registerListing(20_000L);
        Long tradeId = jsonLong(purchase(listingId, 8_000L), "tradeId");

        mockMvc.perform(post("/api/v1/trades/" + tradeId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"reason\": \"   \" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelReason").doesNotExist());
    }

    @Test
    void tooLongReasonIsRejected() throws Exception {
        Long listingId = registerListing(20_000L);
        Long tradeId = jsonLong(purchase(listingId, 8_000L), "tradeId");

        mockMvc.perform(post("/api/v1/trades/" + tradeId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"reason\": \"" + "가".repeat(256) + "\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    /**
     * 되돌린 수량은 다시 판매 중이므로 계속 묶여 있어야 한다. 여기서 풀면 판매 글에 올라와
     * 있는 수량을 가공이나 생산에도 쓸 수 있게 된다(정책 6.4). 잠금이 RELEASED 되는 것은
     * 판매 글 자체를 내릴 때뿐이다(정책 7.3).
     */
    @Test
    void cancellingATradeKeepsTheListingLockActive() throws Exception {
        Long listingId = registerListing(20_000L);
        Long tradeId = jsonLong(purchase(listingId, 8_000L), "tradeId");
        Long lockId = listingRepository.findById(listingId).orElseThrow().getMaterialLockId();

        mockMvc.perform(post("/api/v1/trades/" + tradeId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk());

        MaterialLock lock = lockRepository.findById(lockId).orElseThrow();
        assertThat(lock.getStatus()).isEqualTo(MaterialLockStatus.ACTIVE);
        assertThat(lock.getAmount()).isEqualTo(20_000L);
    }

    @Test
    void reopensASoldOutListingWhenTheLastTradeIsCancelled() throws Exception {
        Long listingId = registerListing(20_000L);
        Long tradeId = jsonLong(purchase(listingId, 20_000L), "tradeId");

        mockMvc.perform(post("/api/v1/trades/" + tradeId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk());

        assertThat(listingRepository.findById(listingId).orElseThrow().getStatus())
                .isEqualTo(ListingStatus.ON_SALE);
    }

    @Test
    void letsTheSellerCancelAPaidTradeBeforeShipping() throws Exception {
        Long listingId = registerListing(20_000L);
        Long tradeId = jsonLong(purchase(listingId, 3_000L), "tradeId");
        payFor(tradeId);

        mockMvc.perform(post("/api/v1/trades/" + tradeId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk());

        assertThat(tradeRepository.findById(tradeId).orElseThrow().getStatus())
                .isEqualTo(TradeStatus.CANCELED);
    }

    @Test
    void cancellingAListingReleasesTheLock() throws Exception {
        Long listingId = registerListing(20_000L);
        MaterialListing listing = listingRepository.findById(listingId).orElseThrow();

        mockMvc.perform(delete("/api/v1/listings/" + listingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));

        MaterialLock lock = lockRepository.findById(listing.getMaterialLockId()).orElseThrow();
        // RELEASED 여야 한다. CONSUMED 면 "소비됨" 이력이 거짓이 된다.
        assertThat(lock.getStatus()).isEqualTo(MaterialLockStatus.RELEASED);
    }

    @Test
    void freesTheQuantityForANewListingAfterCancelling() throws Exception {
        Long listingId = registerListing(OWNED_QUANTITY);

        mockMvc.perform(delete("/api/v1/listings/" + listingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk());

        // 잠금이 실제로 풀렸다면 같은 수량을 다시 걸 수 있다.
        mockMvc.perform(post("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(listingRequest(OWNED_QUANTITY))))
                .andExpect(status().isOk());
    }

    @Test
    void refusesToCancelAListingWithAnOngoingTrade() throws Exception {
        // 판매 글을 내리며 잠금을 풀면 배송해야 할 물건을 다른 곳에 또 팔 수 있다.
        Long listingId = registerListing(20_000L);
        purchase(listingId, 3_000L);

        mockMvc.perform(delete("/api/v1/listings/" + listingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_HAS_ONGOING_TRADE"));
    }

    @Test
    void refusesToCancelSomeoneElsesListing() throws Exception {
        Long listingId = registerListing(20_000L);

        mockMvc.perform(delete("/api/v1/listings/" + listingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_OWNED"));
    }

    @Test
    void hidesCancelledListingsFromTheDefaultList() throws Exception {
        Long listingId = registerListing(20_000L);
        mockMvc.perform(delete("/api/v1/listings/" + listingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    // ── 거래 조회 ────────────────────────────────────────────

    @Test
    void hidesATradeFromNonParticipants() throws Exception {
        Long listingId = registerListing(20_000L);
        Long tradeId = jsonLong(purchase(listingId, 1_000L), "tradeId");
        User outsider = userRepository.save(User.register(
                "market-outsider@example.com", "{noop}x", "제3자", "010-6666-6666", UserRole.BRAND));

        mockMvc.perform(get("/api/v1/trades/" + tradeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TRADE_NOT_PARTICIPANT"));
    }

    // ── 내 거래 목록 ─────────────────────────────────────────

    @Test
    void listsWhatIBought() throws Exception {
        Long listingId = registerListing(20_000L);
        purchase(listingId, 3_000L);

        mockMvc.perform(get("/api/v1/trades")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].materialName").value("폐데님 원단"))
                .andExpect(jsonPath("$.data.content[0].sellerName").value("공급자"))
                .andExpect(jsonPath("$.data.content[0].buyerName").value("브랜드"))
                .andExpect(jsonPath("$.data.content[0].pricePerKg").value(PRICE_PER_KG));
    }

    @Test
    void listsWhatISold() throws Exception {
        Long listingId = registerListing(20_000L);
        purchase(listingId, 3_000L);

        mockMvc.perform(get("/api/v1/trades")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .param("side", "SELL"))
                .andExpect(jsonPath("$.data.content.length()").value(1));

        // 판매자에게 구매 목록은 비어 있어야 한다.
        mockMvc.perform(get("/api/v1/trades")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void filtersMyTradesByStatus() throws Exception {
        Long listingId = registerListing(20_000L);
        Long tradeId = jsonLong(purchase(listingId, 3_000L), "tradeId");
        purchase(listingId, 1_000L);
        payFor(tradeId);

        mockMvc.perform(get("/api/v1/trades")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .param("status", "PAID"))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].tradeId").value(tradeId));
    }

    @Test
    void neverShowsSomeoneElsesTrades() throws Exception {
        // 사용자 id 를 파라미터로 받지 않는 것이 이 테스트의 요지다.
        Long listingId = registerListing(20_000L);
        purchase(listingId, 3_000L);
        User outsider = userRepository.save(User.register(
                "market-outsider2@example.com", "{noop}x", "제3자", "010-7777-7777", UserRole.BRAND));

        mockMvc.perform(get("/api/v1/trades")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(outsider)))
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void carriesMaterialDescriptionOnTheListing() throws Exception {
        // 판매 글에는 설명이 없다. 소재의 설명을 그대로 내려보낸다.
        Long listingId = registerListing(20_000L);

        mockMvc.perform(get("/api/v1/listings/" + listingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(jsonPath("$.data.materialDescription").value("수거된 청바지"));
    }

    // ── helpers ─────────────────────────────────────────────

    private Map<String, Object> listingRequest(long quantity) {
        Map<String, Object> request = new HashMap<>();
        request.put("materialId", material.getId());
        request.put("quantity", quantity);
        request.put("unit", "G");
        request.put("pricePerKg", PRICE_PER_KG);
        return request;
    }

    private Map<String, Object> tradeRequest(Long listingId, long quantity) {
        Map<String, Object> request = new HashMap<>();
        request.put("listingId", listingId);
        request.put("quantity", quantity);
        request.put("unit", "G");
        request.put("paymentMethod", "MOCK");
        // 배송지는 필수다. 없으면 판매자가 어디로 보낼지 알 수 없어 발송이 막힌다
        request.put("receiverName", "김구매");
        request.put("receiverPhone", "010-1111-1111");
        request.put("zipCode", "04766");
        request.put("address", "서울시 성동구");
        request.put("addressDetail", "101동 1001호");
        return request;
    }

    private Map<String, Object> approveRequest() {
        Map<String, Object> request = new HashMap<>();
        // Mock 게이트웨이는 토큰을 검사하지 않는다. 실제 PG 에서는 결제창이 돌려준 값이다.
        request.put("pgToken", "TEST-PG-TOKEN");
        return request;
    }

    /**
     * 필터 조합용 판매글 세 개.
     *
     * <p>종류·출처·등급·가격을 서로 다르게 잡아 조건이 실제로 교집합을 만드는지 볼 수 있게 한다.
     *
     * <pre>
     *   데님 / WASTE  / A / 14,000원
     *   폐면 / WASTE  / B /  4,200원
     *   가죽 / VIRGIN / A / 52,000원
     * </pre>
     */
    private void registerThreeListings() throws Exception {
        registerListing(10_000L);
        createdListingId(listingRequestFor(cotton.getId(), 10_000L, 4_200L));
        createdListingId(listingRequestFor(silk.getId(), 10_000L, 52_000L));
    }

    private Map<String, Object> listingRequestFor(Long materialId, long quantity, long pricePerKg) {
        Map<String, Object> request = new HashMap<>();
        request.put("materialId", materialId);
        request.put("quantity", quantity);
        request.put("unit", "G");
        request.put("pricePerKg", pricePerKg);
        return request;
    }

    private Long registerListing(long quantity) throws Exception {
        return createdListingId(listingRequest(quantity));
    }

    private Long createdListingId(Map<String, Object> request) throws Exception {
        return createdListingId(request, sellerToken, null);
    }

    private Long createdListingId(Map<String, Object> request, String token, Long materialId)
            throws Exception {
        if (materialId != null) {
            request.put("materialId", materialId);
        }
        String response = mockMvc.perform(post("/api/v1/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return jsonLong(response, "listingId");
    }

    private String purchase(Long listingId, long quantity) throws Exception {
        return mockMvc.perform(post("/api/v1/trades")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(tradeRequest(listingId, quantity))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /** 준비 → 승인 두 단계를 한 번에. 결제 이후 상태가 필요한 테스트가 쓴다. */
    private void payFor(Long tradeId) throws Exception {
        readyPayment(tradeId);
        mockMvc.perform(post("/api/v1/trades/" + tradeId + "/payment/approve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(approveRequest())))
                .andExpect(status().isOk());
    }

    private void readyPayment(Long tradeId) throws Exception {
        mockMvc.perform(post("/api/v1/trades/" + tradeId + "/payment/ready")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk());
    }

    private Long jsonLong(String response, String field) throws Exception {
        return objectMapper.readTree(response).path("data").path(field).asLong();
    }

    private String jsonText(String response, String field) throws Exception {
        return objectMapper.readTree(response).path("data").path(field).asText();
    }

    private String body(Map<String, Object> request) throws Exception {
        return objectMapper.writeValueAsString(request);
    }

    private String tokenFor(User user) {
        return jwtTokenService
                .issueAccessToken(String.valueOf(user.getId()), List.of(user.getRole().name()))
                .value();
    }

    private void clean() {
        notificationRepository.deleteAll();
        materialEventRepository.deleteAll();
        tradeRepository.deleteAll();
        listingRepository.deleteAll();
        lockRepository.deleteAll();
        balanceRepository.deleteAll();
        materialRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }
}
