package com.ssafy.upcycle.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.common.jpa.config.JpaAuditingConfig;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * 활성 지갑 조회와 이력 조회.
 *
 * <p><b>활성 단일성 제약은 여기서 검증하지 않는다.</b> 그 제약은 schema.sql 의
 * {@code active_user_id} 생성 컬럼 UNIQUE 이고, 테스트 프로파일은 {@code create-drop} 이라
 * 엔티티에서 테이블을 만들기 때문에 이 테스트에는 그 컬럼 자체가 없다.
 * DDL 검증은 {@code UserWalletActiveConstraintTests} 가 raw H2 + schema.sql 로 한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class UserWalletRepositoryTests {

    private static final String ADDRESS_A = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String ADDRESS_B = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String ADDRESS_C = "0xcccccccccccccccccccccccccccccccccccccccc";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 4, 12, 0);

    @Autowired
    private UserWalletRepository userWalletRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Long userId;

    @BeforeEach
    void setUp() {
        User user = entityManager.persistAndFlush(
                User.register("owner@example.com", "{noop}x", "소유자", "010-0000-0000", UserRole.SUPPLIER));
        userId = user.getId();
    }

    @Test
    void allowsManyInactiveWalletsAlongsideOneActive() {
        // 이력 보존이 이 스키마의 존재 이유다.
        UserWallet first = userWalletRepository.saveAndFlush(UserWallet.link(userId, ADDRESS_A, 1337L));
        first.deactivate(NOW);
        userWalletRepository.flush();

        UserWallet second = userWalletRepository.saveAndFlush(UserWallet.link(userId, ADDRESS_B, 1337L));
        second.deactivate(NOW.plusHours(1));
        userWalletRepository.flush();

        userWalletRepository.saveAndFlush(UserWallet.link(userId, ADDRESS_C, 1337L));

        assertThat(userWalletRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId)).hasSize(3);
        assertThat(userWalletRepository.findByUserIdAndActiveTrue(userId))
                .get()
                .satisfies(active -> assertThat(active.getWalletAddress()).isEqualTo(ADDRESS_C));
    }

    @Test
    void activeLookupIgnoresDeactivatedWallets() {
        UserWallet wallet = userWalletRepository.saveAndFlush(UserWallet.link(userId, ADDRESS_A, 1337L));
        assertThat(userWalletRepository.findByWalletAddressAndActiveTrue(ADDRESS_A)).isPresent();

        wallet.deactivate(NOW);
        userWalletRepository.flush();
        entityManager.clear();

        assertThat(userWalletRepository.findByWalletAddressAndActiveTrue(ADDRESS_A))
                .as("비활성 지갑은 없는 것과 같게 취급한다")
                .isEmpty();
        assertThat(userWalletRepository.findByWalletAddress(ADDRESS_A))
                .as("주소 자리는 여전히 점유한다 (전역 UNIQUE)")
                .isPresent();
    }

    @Test
    void findByIdAndUserIdHidesOtherUsersWallets() {
        UserWallet mine = userWalletRepository.saveAndFlush(UserWallet.link(userId, ADDRESS_A, 1337L));
        User other = entityManager.persistAndFlush(
                User.register("other@example.com", "{noop}x", "타인", "010-9999-9999", UserRole.BRAND));

        assertThat(userWalletRepository.findByIdAndUserId(mine.getId(), userId)).isPresent();
        assertThat(userWalletRepository.findByIdAndUserId(mine.getId(), other.getId())).isEmpty();
    }

    @Test
    void historyIsOrderedNewestFirst() {
        UserWallet first = userWalletRepository.saveAndFlush(UserWallet.link(userId, ADDRESS_A, 1337L));
        first.deactivate(NOW);
        userWalletRepository.flush();
        UserWallet second = userWalletRepository.saveAndFlush(UserWallet.link(userId, ADDRESS_B, 1337L));

        assertThat(userWalletRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId))
                .extracting(UserWallet::getId)
                .containsExactly(second.getId(), first.getId());
    }
}
