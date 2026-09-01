package com.ssafy.upcycle.common.jpa.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.jpa.impl.JPAQueryFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackageClasses = QuerydslTestEntity.class)
@Import({JpaAuditingConfig.class, QuerydslConfig.class})
class QuerydslConfigTests {
    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JPAQueryFactory queryFactory;

    @Test
    void generatedQTypeExecutesRealH2Query() {
        QuerydslTestEntity saved = new QuerydslTestEntity("copper");
        entityManager.persistAndFlush(saved);
        entityManager.clear();
        QQuerydslTestEntity entity = QQuerydslTestEntity.querydslTestEntity;

        QuerydslTestEntity found = queryFactory.selectFrom(entity)
                .where(entity.name.eq("copper"))
                .fetchOne();

        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("copper");
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }
}
