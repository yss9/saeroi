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
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackageClasses = BaseEntityAuditingTests.AuditedEntity.class)
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
