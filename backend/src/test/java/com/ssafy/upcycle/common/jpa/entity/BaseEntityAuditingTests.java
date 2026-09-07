package com.ssafy.upcycle.common.jpa.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.common.jpa.config.JpaAuditingConfig;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * {@code @EntityScan} 으로 스캔 범위를 이 테스트 엔티티만으로 좁히지 않는다.
 * {@code @DataJpaTest} 는 엔티티 스캔과 무관하게 프로덕션 Repository 빈을 모두 만들기 때문에,
 * 범위를 좁히면 그 Repository 들이 참조하는 엔티티가 managed type 이 아니게 되어
 * 컨텍스트 로딩 자체가 실패한다. 기본 스캔 범위({@code com.ssafy.upcycle})가
 * 이 테스트 엔티티도 함께 잡으므로 좁힐 이유도 없다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class BaseEntityAuditingTests {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testProfileUsesMySqlCompatibleH2Database() {
        String compatibilityMode = jdbcTemplate.queryForObject(
                "SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS "
                        + "WHERE SETTING_NAME = 'MODE'",
                String.class);

        assertThat(compatibilityMode).isEqualTo("MySQL");
    }

    @Test
    void persistedEntityGetsAuditTimestamps() {
        AuditedEntity entity = new AuditedEntity();

        entityManager.persistAndFlush(entity);

        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }

    @Entity(name = "AuditedEntity")
    static class AuditedEntity extends BaseEntity {

        @Id
        @GeneratedValue
        private Long id;
    }
}
