package com.ssafy.upcycle.trade.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialLock;
import com.ssafy.upcycle.material.entity.MaterialLockStatus;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.trade.entity.ListingStatus;
import com.ssafy.upcycle.trade.entity.MaterialListing;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import com.ssafy.upcycle.trade.repository.MaterialListingRepository;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
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

    private User seller;
    private User buyer;
    private Material material;
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
        mockMvc.perform(get("/api/listings")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAConsumerFromListing() throws Exception {
        User consumer = userRepository.save(User.register(
                "market-consumer@example.com", "{noop}x", "소비자", "010-2222-2222", UserRole.CONSUMER));

        mockMvc.perform(post("/api/listings")
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

        mockMvc.perform(post("/api/trades")
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
        mockMvc.perform(post("/api/listings")
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

        mockMvc.perform(post("/api/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(listingRequest(15_000L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_QUANTITY_EXCEEDS_AVAILABLE"));
    }

    @Test
    void rejectsListingAMaterialTheSellerDoesNotHold() throws Exception {
        Material other = materialRepository.save(Material.register(
                "MAT-900002",
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

        mockMvc.perform(post("/api/listings")
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

        mockMvc.perform(post("/api/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(request)))
                .andExpect(status().isBadRequest());
    }

    // ── 목록·검색·필터 ───────────────────────────────────────

    @Test
    void listsOnSaleListingsWithMaterialInformation() throws Exception {
        registerListing(20_000L);

        mockMvc.perform(get("/api/listings")
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

        mockMvc.perform(get("/api/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .param("materialType", "COTTON"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void filtersByPriceRange() throws Exception {
        registerListing(20_000L);

        mockMvc.perform(get("/api/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .param("maxPricePerKg", String.valueOf(PRICE_PER_KG - 1)))
                .andExpect(jsonPath("$.data.content.length()").value(0));

        mockMvc.perform(get("/api/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .param("minPricePerKg", String.valueOf(PRICE_PER_KG)))
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    @Test
    void searchesByMaterialName() throws Exception {
        registerListing(20_000L);

        mockMvc.perform(get("/api/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .param("keyword", "데님"))
                .andExpect(jsonPath("$.data.content.length()").value(1));

        mockMvc.perform(get("/api/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .param("keyword", "면"))
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void filtersToOneSellerForTheSupplierMarket() throws Exception {
        // Supplier Market 은 sellerId 필터 하나로 같은 엔드포인트를 쓴다.
        registerListing(20_000L);

        mockMvc.perform(get("/api/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .param("sellerId", String.valueOf(seller.getId())))
                .andExpect(jsonPath("$.data.content.length()").value(1));

        mockMvc.perform(get("/api/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .param("sellerId", String.valueOf(buyer.getId())))
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void capsThePageSize() throws Exception {
        // size=100000 한 번으로 전체 테이블을 끌어오지 못해야 한다.
        registerListing(20_000L);

        mockMvc.perform(get("/api/listings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .param("size", "100000"))
                .andExpect(jsonPath("$.data.size").value(100));
    }

    // ── 금액 계산·부분 구매 ──────────────────────────────────

    @Test
    void quotesThePriceWithoutChangingAnything() throws Exception {
        Long listingId = registerListing(20_000L);

        mockMvc.perform(get("/api/trades/quote")
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

        mockMvc.perform(get("/api/trades/quote")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .param("listingId", String.valueOf(listingId))
                        .param("quantity", "25")
                        .param("unit", "KG"))
                .andExpect(jsonPath("$.data.purchasable").value(false));
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

        mockMvc.perform(post("/api/trades")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(tradeRequest(listingId, 6_000L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_QUANTITY_EXCEEDS_AVAILABLE"));
    }

    @Test
    void rejectsBuyingOwnListing() throws Exception {
        Long listingId = registerListing(20_000L);
        User brandSeller = userRepository.save(User.register(
                "market-brand-seller@example.com", "{noop}x", "브랜드셀러", "010-4444-4444", UserRole.BRAND));
        walletRepository.save(UserWallet.link(
                brandSeller.getId(), "0xdddddddddddddddddddddddddddddddddddddddd", SSAFY_CHAIN_ID));
        // 판매자가 BRAND 여야 구매 자격 검사를 통과해 자기 글 구매 검사까지 도달한다.
        MaterialListing listing = listingRepository.findById(listingId).orElseThrow();
        MaterialBalance balance = balanceRepository.save(MaterialBalance.of(
                listing.getMaterialId(), brandSeller.getId(), "0xdddddddddddddddddddddddddddddddddddddddd", 5_000L));
        Long ownListingId = createdListingId(
                listingRequest(5_000L), tokenFor(brandSeller), balance.getMaterialId());

        mockMvc.perform(post("/api/trades")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(brandSeller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(tradeRequest(ownListingId, 1_000L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRADE_SELF_PURCHASE"));
    }

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

        mockMvc.perform(post("/api/trades")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(walletless))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(tradeRequest(listingId, 1_000L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRADE_WALLET_NOT_LINKED"));
    }

    // ── 결제 ────────────────────────────────────────────────

    @Test
    void confirmsPaymentAndRecordsTheApprovalReference() throws Exception {
        Long listingId = registerListing(20_000L);
        Long tradeId = jsonLong(purchase(listingId, 3_000L), "tradeId");

        mockMvc.perform(post("/api/trades/" + tradeId + "/payment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(paymentRequest("TID-0001", 42_000L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.paymentReference").value("TID-0001"))
                .andExpect(jsonPath("$.data.paidAt").isNotEmpty());
    }

    @Test
    void rejectsAnApprovalForTheWrongAmount() throws Exception {
        // 이 검사가 없으면 클라이언트가 1원 승인을 알려도 거래가 PAID 가 된다.
        Long listingId = registerListing(20_000L);
        Long tradeId = jsonLong(purchase(listingId, 3_000L), "tradeId");

        mockMvc.perform(post("/api/trades/" + tradeId + "/payment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(paymentRequest("TID-0002", 1L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"));
    }

    @Test
    void rejectsADuplicatePayment() throws Exception {
        Long listingId = registerListing(20_000L);
        Long tradeId = jsonLong(purchase(listingId, 3_000L), "tradeId");
        payFor(tradeId, 42_000L);

        mockMvc.perform(post("/api/trades/" + tradeId + "/payment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(paymentRequest("TID-0003", 42_000L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_STATE_INVALID"));
    }

    @Test
    void rejectsAPaymentFromSomeoneElse() throws Exception {
        Long listingId = registerListing(20_000L);
        Long tradeId = jsonLong(purchase(listingId, 3_000L), "tradeId");

        mockMvc.perform(post("/api/trades/" + tradeId + "/payment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(paymentRequest("TID-0004", 42_000L))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TRADE_NOT_PARTICIPANT"));
    }

    // ── 취소 ────────────────────────────────────────────────

    @Test
    void cancellingATradeGivesTheQuantityBack() throws Exception {
        // 이것이 빠지면 결제창을 닫은 사용자마다 판매 수량이 영구히 사라진다.
        Long listingId = registerListing(20_000L);
        Long tradeId = jsonLong(purchase(listingId, 8_000L), "tradeId");

        mockMvc.perform(post("/api/trades/" + tradeId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));

        assertThat(listingRepository.findById(listingId).orElseThrow().getAvailableQuantity())
                .isEqualTo(20_000L);
    }

    @Test
    void reopensASoldOutListingWhenTheLastTradeIsCancelled() throws Exception {
        Long listingId = registerListing(20_000L);
        Long tradeId = jsonLong(purchase(listingId, 20_000L), "tradeId");

        mockMvc.perform(post("/api/trades/" + tradeId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk());

        assertThat(listingRepository.findById(listingId).orElseThrow().getStatus())
                .isEqualTo(ListingStatus.ON_SALE);
    }

    @Test
    void letsTheSellerCancelAPaidTradeBeforeShipping() throws Exception {
        Long listingId = registerListing(20_000L);
        Long tradeId = jsonLong(purchase(listingId, 3_000L), "tradeId");
        payFor(tradeId, 42_000L);

        mockMvc.perform(post("/api/trades/" + tradeId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk());

        assertThat(tradeRepository.findById(tradeId).orElseThrow().getStatus())
                .isEqualTo(TradeStatus.CANCELED);
    }

    @Test
    void cancellingAListingReleasesTheLock() throws Exception {
        Long listingId = registerListing(20_000L);
        MaterialListing listing = listingRepository.findById(listingId).orElseThrow();

        mockMvc.perform(delete("/api/listings/" + listingId)
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

        mockMvc.perform(delete("/api/listings/" + listingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk());

        // 잠금이 실제로 풀렸다면 같은 수량을 다시 걸 수 있다.
        mockMvc.perform(post("/api/listings")
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

        mockMvc.perform(delete("/api/listings/" + listingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LISTING_HAS_ONGOING_TRADE"));
    }

    @Test
    void refusesToCancelSomeoneElsesListing() throws Exception {
        Long listingId = registerListing(20_000L);

        mockMvc.perform(delete("/api/listings/" + listingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LISTING_NOT_OWNED"));
    }

    @Test
    void hidesCancelledListingsFromTheDefaultList() throws Exception {
        Long listingId = registerListing(20_000L);
        mockMvc.perform(delete("/api/listings/" + listingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/listings")
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

        mockMvc.perform(get("/api/trades/" + tradeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TRADE_NOT_PARTICIPANT"));
    }

    // ── 내 거래 목록 ─────────────────────────────────────────

    @Test
    void listsWhatIBought() throws Exception {
        Long listingId = registerListing(20_000L);
        purchase(listingId, 3_000L);

        mockMvc.perform(get("/api/trades")
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

        mockMvc.perform(get("/api/trades")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                        .param("side", "SELL"))
                .andExpect(jsonPath("$.data.content.length()").value(1));

        // 판매자에게 구매 목록은 비어 있어야 한다.
        mockMvc.perform(get("/api/trades")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void filtersMyTradesByStatus() throws Exception {
        Long listingId = registerListing(20_000L);
        Long tradeId = jsonLong(purchase(listingId, 3_000L), "tradeId");
        purchase(listingId, 1_000L);
        payFor(tradeId, 42_000L);

        mockMvc.perform(get("/api/trades")
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

        mockMvc.perform(get("/api/trades")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(outsider)))
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void carriesMaterialDescriptionOnTheListing() throws Exception {
        // 판매 글에는 설명이 없다. 소재의 설명을 그대로 내려보낸다.
        Long listingId = registerListing(20_000L);

        mockMvc.perform(get("/api/listings/" + listingId)
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
        return request;
    }

    private Map<String, Object> paymentRequest(String reference, long amount) {
        Map<String, Object> request = new HashMap<>();
        request.put("paymentReference", reference);
        request.put("paymentAmount", amount);
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
        String response = mockMvc.perform(post("/api/listings")
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
        return mockMvc.perform(post("/api/trades")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(tradeRequest(listingId, quantity))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private void payFor(Long tradeId, long amount) throws Exception {
        mockMvc.perform(post("/api/trades/" + tradeId + "/payment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(paymentRequest("TID-PAID-" + tradeId, amount))))
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
        tradeRepository.deleteAll();
        listingRepository.deleteAll();
        lockRepository.deleteAll();
        balanceRepository.deleteAll();
        materialRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }
}
