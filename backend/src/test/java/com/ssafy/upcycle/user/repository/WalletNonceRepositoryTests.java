package com.ssafy.upcycle.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.common.jpa.config.JpaAuditingConfig;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.WalletNonce;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class WalletNonceRepositoryTests {

    private static final String ADDRESS = "0xd8da6bf26964af9d7eed9e03e53415d37aa96045";
    private static final String OTHER_ADDRESS = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";
    private static final LocalDateTime EXPIRES = LocalDateTime.of(2026, 9, 4, 12, 5);

    @Autowired
    private WalletNonceRepository walletNonceRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Long userId;

    @BeforeEach
    void setUp() {
        // wallet_nonce.user_id 는 users FK 라 실제 행이 있어야 한다.
        User user = entityManager.persistAndFlush(
                User.register("owner@example.com", "{noop}x", "소유자", "010-0000-0000", UserRole.SUPPLIER));
        userId = user.getId();
    }

    @Test
    void findsByAddressAndNonceTogether() {
        save(ADDRESS, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

        Optional<WalletNonce> found =
                walletNonceRepository.findByWalletAddressAndNonce(ADDRESS, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

        assertThat(found).isPresent();
    }

    @Test
    void doesNotFindANonceIssuedForAnotherWallet() {
        // 지갑 A 의 nonce 를 지갑 B 이름으로 제출하는 경로 — 서명 검증 전에 여기서 끊긴다.
        save(ADDRESS, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

        assertThat(walletNonceRepository
                .findByWalletAddressAndNonce(OTHER_ADDRESS, "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"))
                .isEmpty();
    }

    @Test
    void deleteUnusedRemovesOnlyUnusedNoncesOfThatWallet() {
        WalletNonce used = save(ADDRESS, "cccccccccccccccccccccccccccccccc");
        save(ADDRESS, "dddddddddddddddddddddddddddddddd");
        save(OTHER_ADDRESS, "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");
        walletNonceRepository.markUsed(used.getId(), EXPIRES);
        entityManager.flush();
        entityManager.clear();

        long deleted = walletNonceRepository.deleteByWalletAddressAndUsedAtIsNull(ADDRESS);

        // findAll() 로 세지 않는다. 같은 in-memory H2 를 @SpringBootTest 들이 공유하고
        // 그쪽은 롤백되지 않아 남은 행이 섞인다. 이 테스트가 만든 행만 직접 확인한다.
        assertThat(deleted).isEqualTo(1);
        assertThat(walletNonceRepository.findByWalletAddressAndNonce(ADDRESS, "cccccccccccccccccccccccccccccccc"))
                .as("사용된 nonce 는 남아야 한다")
                .isPresent();
        assertThat(walletNonceRepository.findByWalletAddressAndNonce(ADDRESS, "dddddddddddddddddddddddddddddddd"))
                .as("같은 지갑의 미사용 nonce 만 지워진다")
                .isEmpty();
        assertThat(walletNonceRepository.findByWalletAddressAndNonce(OTHER_ADDRESS, "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"))
                .as("다른 지갑은 건드리지 않는다")
                .isPresent();
    }

    @Test
    void markUsedSucceedsOnceAndThenReportsNoRowsAffected() {
        // 1회용 보장을 SQL 레벨에서 직접 확인한다. 두 번째 호출이 0 을 돌려주는 것이
        // 동시 요청 중 하나만 통과한다는 근거다.
        WalletNonce nonce = save(ADDRESS, "ffffffffffffffffffffffffffffffff");

        assertThat(walletNonceRepository.markUsed(nonce.getId(), EXPIRES)).isEqualTo(1);
        assertThat(walletNonceRepository.markUsed(nonce.getId(), EXPIRES)).isZero();
    }

    @Test
    void markUsedWritesTheTimestamp() {
        WalletNonce nonce = save(ADDRESS, "11111111111111111111111111111111");

        walletNonceRepository.markUsed(nonce.getId(), EXPIRES);
        entityManager.clear();

        assertThat(walletNonceRepository.findById(nonce.getId()))
                .get()
                .extracting(WalletNonce::getUsedAt)
                .isEqualTo(EXPIRES);
    }

    private WalletNonce save(String address, String nonce) {
        return entityManager.persistAndFlush(
                WalletNonce.issue(userId, address, 1337L, nonce, EXPIRES));
    }
}
