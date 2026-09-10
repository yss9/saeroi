package com.ssafy.upcycle.blockchain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.blockchain.dto.BlockchainTransactionResponse;
import com.ssafy.upcycle.blockchain.entity.BlockchainReferenceType;
import com.ssafy.upcycle.blockchain.entity.BlockchainTransactionStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code BlockchainTransactionQueryService} — 한 대상에 기록된 트랜잭션 전체를 최신순으로
 * 돌려주는지, 다른 대상·다른 타입은 섞이지 않는지.
 *
 * <p>{@code BlockchainVerificationServiceTests} 와 같은 이유로 행을 SQL 로 직접 넣는다 —
 * 엔티티에 임의 상태를 만드는 생성자가 없다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(BlockchainTransactionQueryService.class)
class BlockchainTransactionQueryServiceTests {

    private static final long MATERIAL_ID = 42L;

    @Autowired
    private BlockchainTransactionQueryService queryService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void withoutAnyTransactionReturnsAnEmptyList() {
        assertThat(queryService.findAll(BlockchainReferenceType.MATERIAL, MATERIAL_ID)).isEmpty();
    }

    @Test
    void returnsEveryTransactionRegardlessOfFunctionName() {
        // 같은 소재에 mint 링크와 출처 검증이 둘 다 기록될 수 있다.
        insert("MATERIAL", MATERIAL_ID, "linkMaterialToken", "SUCCESS",
                LocalDateTime.of(2026, 9, 1, 10, 0), 1);
        insert("MATERIAL", MATERIAL_ID, "verifySource", "SUCCESS",
                LocalDateTime.of(2026, 9, 2, 10, 0), 2);

        List<BlockchainTransactionResponse> result =
                queryService.findAll(BlockchainReferenceType.MATERIAL, MATERIAL_ID);

        assertThat(result).hasSize(2)
                .extracting(BlockchainTransactionResponse::functionName)
                .containsExactlyInAnyOrder("linkMaterialToken", "verifySource");
    }

    @Test
    void ordersByConfirmedAtDescending() {
        insert("MATERIAL", MATERIAL_ID, "linkMaterialToken", "SUCCESS",
                LocalDateTime.of(2026, 9, 1, 10, 0), 3);
        insert("MATERIAL", MATERIAL_ID, "verifySource", "SUCCESS",
                LocalDateTime.of(2026, 9, 3, 10, 0), 4);
        insert("MATERIAL", MATERIAL_ID, "linkMaterialToken", "SUCCESS",
                LocalDateTime.of(2026, 9, 2, 10, 0), 5);

        List<BlockchainTransactionResponse> result =
                queryService.findAll(BlockchainReferenceType.MATERIAL, MATERIAL_ID);

        assertThat(result)
                .extracting(BlockchainTransactionResponse::confirmedAt)
                .containsExactly(
                        LocalDateTime.of(2026, 9, 3, 10, 0),
                        LocalDateTime.of(2026, 9, 2, 10, 0),
                        LocalDateTime.of(2026, 9, 1, 10, 0));
    }

    @Test
    void doesNotMixInAnotherReferenceType() {
        // 7번 거래를 7번 소재로 읽으면 안 된다 — reference_id 는 타입마다 따로 매겨진다.
        insert("TRADE", MATERIAL_ID, "confirmReceipt", "SUCCESS",
                LocalDateTime.of(2026, 9, 1, 10, 0), 6);

        assertThat(queryService.findAll(BlockchainReferenceType.MATERIAL, MATERIAL_ID)).isEmpty();
    }

    @Test
    void doesNotMixInAnotherReferenceId() {
        insert("MATERIAL", MATERIAL_ID + 1, "linkMaterialToken", "SUCCESS",
                LocalDateTime.of(2026, 9, 1, 10, 0), 7);

        assertThat(queryService.findAll(BlockchainReferenceType.MATERIAL, MATERIAL_ID)).isEmpty();
    }

    @Test
    void mapsEveryFieldOfTheResponse() {
        insert("MATERIAL", MATERIAL_ID, "verifySource", "SUCCESS",
                LocalDateTime.of(2026, 9, 1, 10, 0), 8);

        BlockchainTransactionResponse response =
                queryService.findAll(BlockchainReferenceType.MATERIAL, MATERIAL_ID).getFirst();

        assertThat(response.functionName()).isEqualTo("verifySource");
        assertThat(response.txHash()).hasSize(66);
        assertThat(response.blockNumber()).isEqualTo(100L);
        assertThat(response.fromWallet()).hasSize(42);
        assertThat(response.status()).isEqualTo(BlockchainTransactionStatus.SUCCESS);
        assertThat(response.confirmedAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 10, 0));
    }

    private void insert(
            String referenceType,
            long referenceId,
            String functionName,
            String status,
            LocalDateTime confirmedAt,
            int seq) {
        jdbc.update(
                """
                INSERT INTO blockchain_transaction (
                    chain_id, contract_address, tx_hash, reference_type, reference_id,
                    function_name, from_wallet, status, block_number, log_index, created_at,
                    confirmed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                31221L,
                "0x" + "a".repeat(40),
                // UNIQUE (chain_id, tx_hash, log_index) 가 걸려 있어 호출마다 달라야 한다.
                "0x" + String.format("%064d", seq),
                referenceType,
                referenceId,
                functionName,
                "0x" + "b".repeat(40),
                status,
                100L,
                0,
                confirmedAt,
                confirmedAt);
    }
}
