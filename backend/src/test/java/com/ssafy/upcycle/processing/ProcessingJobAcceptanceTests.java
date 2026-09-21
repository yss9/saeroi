package com.ssafy.upcycle.processing;

import static com.ssafy.upcycle.user.WalletTestSignatures.KEY_PAIR;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.blockchain.ScriptedReceiptClient;
import com.ssafy.upcycle.blockchain.service.ProcessingReceiptVerifier;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.web3j.crypto.Credentials;

/**
 * Task 1: 결제 직후에도 온체인 작업을 등록할 수 있게 한다.
 *
 * <p>{@code ProcessingChainService.linkJob} 은 지금까지 작업 상태를 보지 않았다. 이 테스트는
 * {@code PAID} 에서는 등록이 되고, 결제가 끝나지 않은 {@code CREATED} 에서는 막히는지 증명한다.
 *
 * <p>{@code ProcessingRequestTests} 와 같은 픽스처 패턴(@SpringBootTest + MockMvc + jwt())을
 * 그대로 따른다 — 별도의 서비스 레벨 테스트 기반 클래스는 이 저장소에 없다.
 */
@SpringBootTest(properties = {"app.processing.expiry.enabled=false",
        "app.blockchain.chain-id=31337",
        "app.blockchain.material-token-address=0x1111111111111111111111111111111111111111",
        "app.blockchain.marketplace-address=0x2222222222222222222222222222222222222222",
        "app.blockchain.processing-registry-address=0x3333333333333333333333333333333333333333",
        "spring.datasource.url=jdbc:h2:mem:processing-tests;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class ProcessingJobAcceptanceTests {
    private static final String PROCESSING_REGISTRY = "0x3333333333333333333333333333333333333333";
    @Autowired ScriptedReceiptClient chain;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired UserWalletRepository wallets;
    @Autowired MaterialRepository materials;
    @Autowired MaterialBalanceRepository balances;
    @MockitoBean ProcessingReceiptVerifier receiptVerifier;
    Long brandId;
    Long processorId;
    Long materialId;
    String brandWallet;

    @BeforeEach
    void fixture() {
        brandWallet = Credentials.create(KEY_PAIR).getAddress();
        // 이 지갑에서 온 트랜잭션은 모두 성공한 것으로 답한다 — chain 링크는 해시별
        // 시나리오가 필요 없다(브랜드 한 번의 서명만 확인하면 된다).
        chain.expect(PROCESSING_REGISTRY, brandWallet);
        for (String table : List.of("blockchain_transaction", "user_notification", "processing_signature_challenge",
                "evidence_file", "material_lineage", "processing_output", "material_event", "processing_input",
                "processing_job", "material_lock", "processing_request_type", "processing_request_input",
                "processing_request", "processor_capability", "processor_profile", "material_balance", "material",
                "user_wallet", "company_profile", "users")) {
            jdbc.update("delete from " + table);
        }
        brandId = users.save(User.register("brand@test.com", "hash", "brand", "010", UserRole.BRAND)).getId();
        processorId = users.save(User.register("processor@test.com", "hash", "processor", "010", UserRole.PROCESSOR)).getId();
        jdbc.update("insert into company_profile(user_id,company_name,business_number,zip_code,address,role_approval_status) values (?,?,?,?,?,'APPROVED')",
                brandId, "brand", "brand-business", "00000", "Seoul");
        jdbc.update("insert into company_profile(user_id,company_name,business_number,zip_code,address,role_approval_status) values (?,?,?,?,?,'APPROVED')",
                processorId, "processor", "processor-business", "00000", "Seoul");
        materialId = materials.save(Material.register("M1", MaterialType.DENIM, MaterialRootOrigin.WASTE,
                "denim", null, "A", 1000, null, null, brandId)).getId();
        balances.save(MaterialBalance.of(materialId, brandId, brandWallet, 1000));
        jdbc.update("insert into processor_profile(user_id, region) values (?, 'Seoul')", processorId);
        jdbc.update("insert into processor_capability(processor_profile_id, material_type, process_type, daily_capacity, price_per_kg) values (?, 'DENIM', 'CLEANING', 1000, 1000)",
                processorId);
        jdbc.update("delete from user_wallet");
        wallets.save(UserWallet.link(brandId, brandWallet, 31337));
        wallets.save(UserWallet.link(processorId, "0x" + "b".repeat(40), 31337));
    }

    @Test
    @DisplayName("결제가 끝난 작업은 가공사 수락 전에도 온체인에 등록할 수 있다")
    void linksChainWhilePaid() throws Exception {
        long jobId = paidJob();

        mvc.perform(chainLink(jobId, 11, 22))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.chainJobId").value(11))
                .andExpect(jsonPath("data.status").value("PAID"));
    }

    @Test
    @DisplayName("결제 전 작업은 온체인에 등록할 수 없다")
    void rejectsChainLinkBeforePayment() throws Exception {
        long jobId = createdJob();

        mvc.perform(chainLink(jobId, 11, 22))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("PROCESSING_INVALID_STATE"));
    }

    /** 요청이 곧 작업을 만든다 — 결제 전(CREATED) 작업의 jobId 를 응답에서 바로 받는다. */
    private long createdJob() throws Exception {
        String body = "{\"materialId\":" + materialId + ",\"processorId\":" + processorId
                + ",\"quantity\":300,\"processTypes\":[\"CLEANING\"]}";
        var created = mvc.perform(post("/api/v1/processing-requests")
                        .with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(created.getResponse().getContentAsString()).path("data").path("jobId").asLong();
    }

    /** 위 작업을 결제까지 끝낸다(PAID). 가공사 수락은 아직이다. */
    private long paidJob() throws Exception {
        long jobId = createdJob();
        mvc.perform(post("/api/v1/processing-jobs/{id}/payment", jobId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString()))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/processing-jobs/{id}/payment/confirm", jobId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pgToken\":\"mock-token\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.status").value("PAID"));
        return jobId;
    }

    private MockHttpServletRequestBuilder chainLink(long jobId, long onChainId, long chainLockId) {
        String txHash = "0x" + String.format("%064x", jobId);
        return post("/api/v1/processing-jobs/{id}/chain", jobId)
                .with(jwt().jwt(j -> j.subject(brandId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"onChainId\":" + onChainId + ",\"chainLockId\":" + chainLockId
                        + ",\"txHash\":\"" + txHash + "\",\"blockNumber\":1}");
    }
}
