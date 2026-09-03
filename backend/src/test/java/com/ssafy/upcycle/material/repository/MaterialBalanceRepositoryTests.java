package com.ssafy.upcycle.material.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.common.jpa.config.JpaAuditingConfig;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * FK 무결성은 여기서 검증되지 않는다. material_id 와 owner_id 를 연관관계가 아닌 값으로
 * 매핑했으므로 Hibernate 가 테스트 스키마에 FK 제약을 만들지 않는다. 실제 MySQL 에는
 * FK 가 있으니, 없는 소재나 사용자를 가리키는 balance 는 운영에서 거부된다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class MaterialBalanceRepositoryTests {

    private static final String WALLET = "0x1234567890abcdef1234567890abcdef12345678";

    @Autowired
    private MaterialBalanceRepository materialBalanceRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void balanceIsFoundByMaterialAndOwner() {
        materialBalanceRepository.save(MaterialBalance.of(1L, 10L, WALLET, 20_000L));
        entityManager.flush();
        entityManager.clear();

        Optional<MaterialBalance> found =
                materialBalanceRepository.findByMaterialIdAndOwnerId(1L, 10L);

        assertThat(found).isPresent();
        assertThat(found.get().getQuantity()).isEqualTo(20_000L);
        assertThat(found.get().getWalletAddress()).isEqualTo(WALLET);
    }

    @Test
    void differentOwnersHoldSeparateBalancesOfTheSameMaterial() {
        materialBalanceRepository.save(MaterialBalance.of(1L, 10L, WALLET, 30_000L));
        materialBalanceRepository.save(MaterialBalance.of(1L, 11L, WALLET, 20_000L));
        entityManager.flush();
        entityManager.clear();

        assertThat(materialBalanceRepository.findByMaterialIdAndOwnerId(1L, 10L))
                .get()
                .extracting(MaterialBalance::getQuantity)
                .isEqualTo(30_000L);
        assertThat(materialBalanceRepository.findByMaterialIdAndOwnerId(1L, 11L))
                .get()
                .extracting(MaterialBalance::getQuantity)
                .isEqualTo(20_000L);
    }

    @Test
    void ownerCanListEveryMaterialTheyHold() {
        materialBalanceRepository.save(MaterialBalance.of(1L, 10L, WALLET, 30_000L));
        materialBalanceRepository.save(MaterialBalance.of(2L, 10L, WALLET, 20_000L));
        materialBalanceRepository.save(MaterialBalance.of(3L, 11L, WALLET, 10_000L));
        entityManager.flush();
        entityManager.clear();

        assertThat(materialBalanceRepository.findAllByOwnerId(10L))
                .hasSize(2)
                .extracting(MaterialBalance::getMaterialId)
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void quantityChangesArePersisted() {
        MaterialBalance saved =
                materialBalanceRepository.save(MaterialBalance.of(1L, 10L, WALLET, 20_000L));
        saved.decrease(5_000L);
        entityManager.flush();
        entityManager.clear();

        assertThat(materialBalanceRepository.findByMaterialIdAndOwnerId(1L, 10L))
                .get()
                .extracting(MaterialBalance::getQuantity)
                .isEqualTo(15_000L);
    }
}
