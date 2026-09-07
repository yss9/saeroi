package com.ssafy.upcycle.blockchain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.blockchain.repository.BlockchainTransactionRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * {@code verifySource} 판정이 <b>정확히 맞는 행에만</b> 반응하는지.
 *
 * <p>조건이 네 개라 하나만 빠져도 조용히 틀린다. 그리고 이 판정의 오답은 방향에 따라
 * 성질이 다르다 — 놓치면 "덜 검증됨" 으로 보이고(회복 가능), 잘못 인정하면
 * <b>일어나지 않은 검증을 표시</b>한다(추적성에서 되돌릴 수 없다).
 * 그래서 인정하지 말아야 할 경우를 하나씩 못 박는다.
 *
 * <p>행은 SQL 로 직접 넣는다. {@code BlockchainTransaction} 에 생성자를 두지 않았기
 * 때문이고, 그건 의도다 — 쓰기 주체(Tx 전송 경로 · 이벤트 리스너)가 아직 없어서
 * 지금 만들면 그쪽이 자기 불변식을 못 걸고 빈 메서드에 맞추게 된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(BlockchainVerificationService.class)
class BlockchainVerificationServiceTests {

    private static final long MATERIAL_ID = 7L;

    @Autowired
    private BlockchainVerificationService verification;

    @Autowired
    private BlockchainTransactionRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void recognizesASuccessfulVerifySourceForThisMaterial() {
        insert("MATERIAL", MATERIAL_ID, "verifySource", "SUCCESS", 1);

        assertThat(verification.hasVerifiedSource(MATERIAL_ID)).isTrue();
    }

    @Test
    void withoutAnyTransactionTheMaterialIsNotVerified() {
        assertThat(verification.hasVerifiedSource(MATERIAL_ID)).isFalse();
    }

    @Test
    void doesNotAcceptAPendingTransaction() {
        // 아직 확정되지 않았다. 블록에 들어가지 못하고 버려질 수 있다.
        insert("MATERIAL", MATERIAL_ID, "verifySource", "PENDING", 2);

        assertThat(verification.hasVerifiedSource(MATERIAL_ID)).isFalse();
    }

    @Test
    void doesNotAcceptAFailedTransaction() {
        // 일어나지 않은 일이다. revert 된 호출을 검증으로 세면 안 된다.
        insert("MATERIAL", MATERIAL_ID, "verifySource", "FAILED", 3);

        assertThat(verification.hasVerifiedSource(MATERIAL_ID)).isFalse();
    }

    @Test
    void doesNotAcceptADifferentContractFunction() {
        // 같은 소재에 mint 는 성공했지만 출처 서명은 없는 상태. 매우 흔한 조합이다.
        insert("MATERIAL", MATERIAL_ID, "registerMaterial", "SUCCESS", 4);

        assertThat(verification.hasVerifiedSource(MATERIAL_ID)).isFalse();
    }

    @Test
    void doesNotAcceptATransactionAboutAnotherReferenceType() {
        // reference_id 는 타입마다 따로 매겨진다. 7번 거래를 7번 소재로 읽으면 안 된다.
        insert("TRADE", MATERIAL_ID, "verifySource", "SUCCESS", 5);

        assertThat(verification.hasVerifiedSource(MATERIAL_ID)).isFalse();
    }

    @Test
    void doesNotAcceptATransactionAboutAnotherMaterial() {
        insert("MATERIAL", MATERIAL_ID + 1, "verifySource", "SUCCESS", 6);

        assertThat(verification.hasVerifiedSource(MATERIAL_ID)).isFalse();
    }

    @Test
    void nullMaterialIdIsNotVerified() {
        // 상세 조회가 아닌 경로에서 id 없이 불릴 수 있다. 예외 대신 false 로 답한다 —
        // 검증 여부를 묻는 질문에 "모른다" 는 곧 "검증 안 됨" 이다.
        insert("MATERIAL", MATERIAL_ID, "verifySource", "SUCCESS", 7);

        assertThat(verification.hasVerifiedSource(null)).isFalse();
    }

    /**
     * 엔티티가 실제로 읽히는지. 컬럼 이름을 하나라도 잘못 적으면 여기서 드러난다
     * (매핑 자체는 {@code BlockchainSchemaMappingTests} 가 본다).
     */
    @Test
    void mapsTheStoredRowBack() {
        insert("MATERIAL", MATERIAL_ID, "verifySource", "SUCCESS", 8);

        assertThat(repository.findAll())
                .singleElement()
                .satisfies(tx -> {
                    assertThat(tx.getReferenceId()).isEqualTo(MATERIAL_ID);
                    assertThat(tx.getFunctionName()).isEqualTo("verifySource");
                    assertThat(tx.getFromWallet()).hasSize(42);
                    assertThat(tx.getTxHash()).hasSize(66);
                    assertThat(tx.getCreatedAt()).isNotNull();
                });
    }

    private void insert(
            String referenceType, long referenceId, String functionName, String status, int seq) {
        jdbc.update(
                """
                INSERT INTO blockchain_transaction (
                    chain_id, contract_address, tx_hash, reference_type, reference_id,
                    function_name, from_wallet, status, block_number, log_index, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                // 운영에서는 DB 기본값(CURRENT_TIMESTAMP)이 채운다. 테스트 DB 는
                // 엔티티에서 생성되어 그 기본값이 없으므로 여기서 직접 넣는다.
                LocalDateTime.of(2026, 9, 7, 16, 30));
    }
}
