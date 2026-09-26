package com.ssafy.upcycle.material.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.blockchain.entity.BlockchainReferenceType;
import com.ssafy.upcycle.blockchain.entity.BlockchainTransaction;
import com.ssafy.upcycle.blockchain.repository.BlockchainTransactionRepository;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialEvent;
import com.ssafy.upcycle.material.entity.MaterialEventReferenceType;
import com.ssafy.upcycle.material.entity.MaterialEventType;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialEventRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.geolocation.GeoCoordinates;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Material Journey Timeline 조회 (S15P21E104-73).
 *
 * <p>고정하는 계약 셋 — <b>시간순</b>, <b>이벤트가 4단계 중 하나로 분류됨</b>,
 * <b>행위자 표시명·지갑·온체인 해시가 채워짐</b>. 세 번째가 핵심이다 — actorId 나
 * referenceId 를 그대로 노출하면 화면은 남의 계정 번호를 보게 된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MaterialJourneyControllerTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyProfileRepository companyProfileRepository;
    @Autowired private UserWalletRepository walletRepository;
    @Autowired private MaterialRepository materialRepository;
    @Autowired private MaterialEventRepository eventRepository;
    @Autowired private BlockchainTransactionRepository blockchainTransactionRepository;

    private static final String WALLET = "0x" + "2".repeat(40);

    private String token;
    private Long materialId;
    private Long supplierId;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        User reader = userRepository.save(User.register(
                "journey-reader@example.com", "{noop}x", "조회자", "010-1111-2222", UserRole.CONSUMER));
        token = jwtTokenService
                .issueAccessToken(String.valueOf(reader.getId()), List.of(reader.getRole().name()))
                .value();

        User supplier = userRepository.save(User.register(
                "journey-supplier@example.com", "{noop}x", "한빛섬유", "010-2222-3333", UserRole.SUPPLIER));
        supplierId = supplier.getId();
        companyProfileRepository.save(CompanyProfile.register(
                supplierId,
                "한빛섬유",
                "123-45-67890",
                "00000",
                "서울특별시 성동구 연무장길 1",
                null,
                new GeoCoordinates(new BigDecimal("37.5446000"), new BigDecimal("127.0559000"))));
        walletRepository.save(UserWallet.link(supplierId, WALLET, 31_221L));

        Material material = materialRepository.save(Material.register(
                "MAT-JOURNEY-1", MaterialType.DENIM, MaterialRootOrigin.WASTE,
                "여정 소재", null, "A", 5_000L, null, null, supplierId));
        materialId = material.getId();

        BlockchainTransaction proof = blockchainTransactionRepository.save(BlockchainTransaction.confirmed(
                31_221L,
                "0x" + "a".repeat(40),
                "0x" + "b".repeat(64),
                BlockchainReferenceType.MATERIAL,
                materialId,
                "verifySource",
                WALLET,
                100L,
                LocalDateTime.of(2026, 9, 10, 12, 5)));

        eventRepository.saveAll(List.of(
                MaterialEvent.record(
                        materialId, MaterialEventType.MATERIAL_REGISTERED, supplierId,
                        MaterialEventReferenceType.MATERIAL, materialId, null, 5_000L,
                        "최초 등록", "reg", LocalDateTime.of(2026, 9, 10, 12, 0)),
                MaterialEvent.record(
                        materialId, MaterialEventType.SOURCE_VERIFIED, supplierId,
                        MaterialEventReferenceType.MATERIAL, materialId, null, null,
                        null, "verify", LocalDateTime.of(2026, 9, 10, 12, 6)),
                MaterialEvent.record(
                        materialId, MaterialEventType.MARKETPLACE_LISTED, supplierId,
                        MaterialEventReferenceType.MATERIAL_LISTING, 999L, null, 5_000L,
                        null, "list", LocalDateTime.of(2026, 9, 11, 9, 0))));
        assert proof != null;
    }

    @AfterEach
    void tearDown() {
        eventRepository.deleteAll();
        blockchainTransactionRepository.deleteAll();
        walletRepository.deleteAll();
        materialRepository.deleteAll();
        companyProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/materials/" + materialId + "/journey"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void classifiesEventsIntoStagesInChronologicalOrder() throws Exception {
        mockMvc.perform(get("/api/materials/" + materialId + "/journey")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.materialId").value(materialId))
                .andExpect(jsonPath("$.data.events.length()").value(3))
                .andExpect(jsonPath("$.data.events[0].eventType").value("MATERIAL_REGISTERED"))
                .andExpect(jsonPath("$.data.events[0].stage").value("SOURCE"))
                .andExpect(jsonPath("$.data.events[0].detail").value("최초 등록"))
                .andExpect(jsonPath("$.data.events[1].eventType").value("SOURCE_VERIFIED"))
                .andExpect(jsonPath("$.data.events[1].stage").value("SOURCE"))
                .andExpect(jsonPath("$.data.events[2].eventType").value("MARKETPLACE_LISTED"))
                .andExpect(jsonPath("$.data.events[2].stage").value("TRANSACTION"));
    }

    @Test
    void resolvesTheActorIntoACompanyNameAndActiveWallet() throws Exception {
        mockMvc.perform(get("/api/materials/" + materialId + "/journey")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events[0].actorName").value("한빛섬유"))
                .andExpect(jsonPath("$.data.events[0].actorWallet").value(WALLET));
    }

    @Test
    void attachesTheOnChainProofWhenOneExistsForTheReferencedRecord() throws Exception {
        // SOURCE_VERIFIED 는 MATERIAL 참조라 방금 심어 둔 verifySource 트랜잭션과 짝지어진다.
        // MARKETPLACE_LISTED 는 MATERIAL_LISTING 참조라 온체인 매핑 대상이 아니라 null 이어야 한다.
        mockMvc.perform(get("/api/materials/" + materialId + "/journey")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events[1].txHash").value("0x" + "b".repeat(64)))
                .andExpect(jsonPath("$.data.events[2].txHash").doesNotExist());
    }

    @Test
    void rejectsAnUnknownMaterial() throws Exception {
        mockMvc.perform(get("/api/materials/999999/journey")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATERIAL_NOT_FOUND"));
    }
}
