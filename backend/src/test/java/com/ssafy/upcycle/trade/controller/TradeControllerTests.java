package com.ssafy.upcycle.trade.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialLock;
import com.ssafy.upcycle.material.entity.MaterialLockPurpose;
import com.ssafy.upcycle.material.entity.MaterialLockReferenceType;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.trade.entity.MaterialListing;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import com.ssafy.upcycle.trade.entity.TradeShipping;
import com.ssafy.upcycle.trade.repository.MaterialListingRepository;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
import com.ssafy.upcycle.trade.repository.TradeShippingRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code @Transactional} 이 붙은 이유는 격리 때문이다. {@code @SpringBootTest} 는
 * 롤백하지 않아서 거래가 커밋된 채 남고, 테스트들이 같은 인메모리 H2 를 공유하므로
 * trade_code UNIQUE 제약과 부딪힌다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TradeControllerTests {

    private static final String SELLER_WALLET = "0x1111111111111111111111111111111111111111";
    private static final String BUYER_WALLET = "0x2222222222222222222222222222222222222222";
    private static final String WAYBILL_BODY = """
            { "carrier": "CJ대한통운", "trackingNumber": "123456789012" }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MaterialTradeRepository tradeRepository;

    @Autowired
    private TradeShippingRepository shippingRepository;

    @Autowired
    private MaterialListingRepository listingRepository;

    @Autowired
    private MaterialBalanceRepository balanceRepository;

    @Autowired
    private MaterialLockRepository lockRepository;

    @Autowired
    private MaterialRepository materialRepository;

    private User seller;
    private User buyer;
    private MaterialTrade trade;
    private long materialId;

    @BeforeEach
    void setUp() {
        seller = userRepository.save(User.register(
                "seller@example.com", "{noop}x", "공급자", "010-0000-0000", UserRole.SUPPLIER));
        buyer = userRepository.save(User.register(
                "buyer@example.com", "{noop}x", "브랜드", "010-1111-1111", UserRole.BRAND));

        materialId = materialRepository.save(Material.register(
                        "MAT-000001", MaterialType.DENIM, MaterialRootOrigin.WASTE,
                        "폐데님", null, null, 30_000L, null, null, seller.getId()))
                .getId();

        MaterialLock lock = lockRepository.save(MaterialLock.create(
                materialId, seller.getId(), 30_000L,
                MaterialLockPurpose.MARKETPLACE, MaterialLockReferenceType.LISTING, 1L, null));
        MaterialListing listing = listingRepository.save(MaterialListing.open(
                materialId, seller.getId(), 30_000L, 10_000L, lock.getId()));
        balanceRepository.save(MaterialBalance.of(
                materialId, seller.getId(), SELLER_WALLET, 30_000L));

        trade = tradeRepository.save(MaterialTrade.create(
                "TRD-000001", listing.getId(), materialId, seller.getId(), buyer.getId(),
                SELLER_WALLET, BUYER_WALLET, 20_000L, 200_000L, PaymentMethod.MOCK, LocalDateTime.now().plusMinutes(30)));
        trade.markPaid("T1234", 200_000L, LocalDateTime.now(), PaymentMethod.MOCK);
        // 발송의 선행 조건이다. 컨트랙트의 markShipped(orderId) 는 그 번호 없이는 부를 수도
        // 없어서, 서버가 먼저 나가면 체인이 모르는 거래가 발송·수령까지 끝나 버린다.
        trade.assignChainTradeId(42L);
        shippingRepository.save(TradeShipping.of(
                trade.getId(), "김구매", "010-1111-1111", "04766", "서울시 성동구", "101동"));
    }

    private void shipTrade() throws Exception {
        mockMvc.perform(patch("/api/v1/trades/{tradeId}/ship", trade.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(seller, UserRole.SUPPLIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WAYBILL_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void sellerShipsTheTrade() throws Exception {
        mockMvc.perform(patch("/api/v1/trades/{tradeId}/ship", trade.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(seller, UserRole.SUPPLIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WAYBILL_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("SHIPPED"))
                .andExpect(jsonPath("$.data.carrier").value("CJ대한통운"))
                .andExpect(jsonPath("$.data.trackingNumber").value("123456789012"))
                .andExpect(jsonPath("$.data.shippedAt").isNotEmpty());
    }

    @Test
    void waybillIsOptional() throws Exception {
        mockMvc.perform(patch("/api/v1/trades/{tradeId}/ship", trade.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(seller, UserRole.SUPPLIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SHIPPED"));
    }

    /** 파는 쪽은 SUPPLIER 뿐이다. 구매자인 BRAND 는 역할 단계에서 막힌다. */
    @Test
    void buyerCannotShip() throws Exception {
        mockMvc.perform(patch("/api/v1/trades/{tradeId}/ship", trade.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyer, UserRole.BRAND))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WAYBILL_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void consumerRoleIsRejected() throws Exception {
        User consumer = userRepository.save(User.register(
                "consumer@example.com", "{noop}x", "소비자", "010-2222-2222", UserRole.CONSUMER));

        mockMvc.perform(patch("/api/v1/trades/{tradeId}/ship", trade.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(consumer, UserRole.CONSUMER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WAYBILL_BODY))
                .andExpect(status().isForbidden());
    }

    /**
     * 역할 검사만으로는 부족하다는 것을 고정한다. 이 요청자는 SUPPLIER 라 역할 단계는
     * 통과하지만 이 거래의 판매자가 아니다.
     */
    @Test
    void anotherSupplierCannotShipSomeoneElsesTrade() throws Exception {
        User otherSupplier = userRepository.save(User.register(
                "other@example.com", "{noop}x", "다른공급자", "010-3333-3333", UserRole.SUPPLIER));

        mockMvc.perform(patch("/api/v1/trades/{tradeId}/ship", trade.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherSupplier, UserRole.SUPPLIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WAYBILL_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_TRADE_SELLER"));
    }

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(patch("/api/v1/trades/{tradeId}/ship", trade.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WAYBILL_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownTradeReturnsNotFound() throws Exception {
        mockMvc.perform(patch("/api/v1/trades/{tradeId}/ship", 9_999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(seller, UserRole.SUPPLIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WAYBILL_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRADE_NOT_FOUND"));
    }

    @Test
    void shippingTwiceConflicts() throws Exception {
        mockMvc.perform(patch("/api/v1/trades/{tradeId}/ship", trade.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(seller, UserRole.SUPPLIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WAYBILL_BODY))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/trades/{tradeId}/ship", trade.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(seller, UserRole.SUPPLIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WAYBILL_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRADE_ALREADY_SHIPPED"));
    }

    /**
     * 프론트에서 보고된 500 을 그대로 재현한다. 폼의 빈 입력은 null 이 아니라 "" 로 오는데,
     * 그것이 "송장 없이 발송" 이라는 정상 경로를 매번 막고 있었다.
     */
    @Test
    void blankWaybillFieldsAreAcceptedAsNoWaybill() throws Exception {
        mockMvc.perform(patch("/api/v1/trades/{tradeId}/ship", trade.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(seller, UserRole.SUPPLIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"carrier\": \"\", \"trackingNumber\": \"\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SHIPPED"))
                .andExpect(jsonPath("$.data.carrier").doesNotExist())
                .andExpect(jsonPath("$.data.trackingNumber").doesNotExist());
    }

    @Test
    void trackingNumberWithoutCarrierIsRejected() throws Exception {
        mockMvc.perform(patch("/api/v1/trades/{tradeId}/ship", trade.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(seller, UserRole.SUPPLIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"trackingNumber\": \"123456789012\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRADE_CARRIER_REQUIRED"));
    }

    @Test
    void tooLongCarrierIsRejected() throws Exception {
        String body = "{ \"carrier\": \"" + "가".repeat(51) + "\" }";

        mockMvc.perform(patch("/api/v1/trades/{tradeId}/ship", trade.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(seller, UserRole.SUPPLIER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void buyerConfirmsReceipt() throws Exception {
        shipTrade();

        mockMvc.perform(post("/api/v1/trades/{tradeId}/receive/confirm", trade.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyer, UserRole.BRAND)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.transferredQuantity").value(20000))
                .andExpect(jsonPath("$.data.buyerQuantity").value(20000))
                // 확정은 체인에 수령이 남은 뒤에만 되므로, 이 값이 있다는 것은 서명이 있었다는 뜻이다.
                .andExpect(jsonPath("$.data.buyerSignedAt").isNotEmpty())
                // 판매자 재고는 이 거래와 무관하다. 구매자에게 내려가면 안 된다
                .andExpect(jsonPath("$.data.sellerQuantity").doesNotExist());
    }

    /** 받는 쪽은 구매자다. 판매자가 대신 확인하면 2-Track 이 성립하지 않는다. */
    @Test
    void sellerCannotConfirmReceipt() throws Exception {
        shipTrade();

        mockMvc.perform(post("/api/v1/trades/{tradeId}/receive/confirm", trade.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(seller, UserRole.SUPPLIER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void unshippedTradeCannotBeConfirmed() throws Exception {
        mockMvc.perform(post("/api/v1/trades/{tradeId}/receive/confirm", trade.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyer, UserRole.BRAND)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRADE_NOT_RECEIVABLE"));
    }

    @Test
    void confirmingReceiptTwiceConflicts() throws Exception {
        shipTrade();

        mockMvc.perform(post("/api/v1/trades/{tradeId}/receive/confirm", trade.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyer, UserRole.BRAND)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/trades/{tradeId}/receive/confirm", trade.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyer, UserRole.BRAND)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRADE_ALREADY_RECEIVED"));
    }

    @Test
    void anonymousReceiptRequestIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/trades/{tradeId}/receive/confirm", trade.getId()))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 수령 확정이 검증 단계를 끌어올린다.
     *
     * <p>판정 근거는 {@code buyer_signed_at} 이고, <b>확정은 체인에 수령이 남은 뒤에만
     * 통과한다</b>({@code TradeReceiptService#requireChainReceipt}). 그래서 확정됐다는 것은
     * 지갑 서명이 실제로 있었다는 뜻이고, 이 시점에 검증으로 올리는 것이 사실에 맞는다.
     *
     * <p>예전에는 순서가 반대라 확정이 서명 없이 그 값을 채웠다. 그래서 서버는 "서명
     * 끝났다" 고 판단해 수령 서명 카드를 띄우지 않았고, 소재는 체인에서 판매자 잠금에
     * 그대로 남아 나중에 가공으로 보낼 때 insufficient material 로 거절당했다.
     *
     * <p>이 테스트는 체인 설정이 없는 환경에서 돈다. 그때는 어긋날 대상이 없어 선행 조건을
     * 건너뛰므로 확정이 통과한다.
     */
    @Test
    void receiptRaisesTheMaterialToTransactionVerified() throws Exception {
        mockMvc.perform(get("/api/materials/{materialId}", materialId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyer, UserRole.BRAND)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationState").value("REGISTERED"));

        shipTrade();
        mockMvc.perform(post("/api/v1/trades/{tradeId}/receive/confirm", trade.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyer, UserRole.BRAND)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/materials/{materialId}", materialId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyer, UserRole.BRAND)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationState").value("TRANSACTION_VERIFIED"));
    }

    /** 배송까지만으로는 올라가지 않는다. 서명이 판정 근거다. */
    @Test
    void shippingAloneDoesNotVerifyTheMaterial() throws Exception {
        shipTrade();

        mockMvc.perform(get("/api/materials/{materialId}", materialId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(seller, UserRole.SUPPLIER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationState").value("REGISTERED"));
    }

    private String bearer(User user, UserRole role) {
        return "Bearer " + jwtTokenService
                .issueAccessToken(String.valueOf(user.getId()), List.of(role.name()))
                .value();
    }
}
