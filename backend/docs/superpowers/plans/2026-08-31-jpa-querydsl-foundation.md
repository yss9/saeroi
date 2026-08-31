# JPA QueryDSL Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Organize shared JPA infrastructure under a clear package and add Jakarta-compatible QueryDSL Q-type generation plus an injectable `JPAQueryFactory`.

**Architecture:** Existing auditing behavior moves without semantic changes into `common.jpa`. QueryDSL 5.1.0 runs through Maven annotation processing and exposes only a `JPAQueryFactory`; domains continue to own their entities and repositories and introduce custom QueryDSL repositories only for demonstrated query needs.

**Tech Stack:** Java 21, Spring Boot 3.5.16, Spring Data JPA, Hibernate, QueryDSL 5.1.0 Jakarta, MySQL, H2 MySQL mode, JUnit 5, Maven Wrapper

**Spec:** `docs/superpowers/specs/2026-08-31-security-jpa-foundation-design.md`

## Global Constraints

- `BaseEntity` remains ID-free and owns only `createdAt` and `updatedAt` auditing fields.
- Production keeps `ddl-auto: validate`, Open Session in View disabled, and environment-backed MySQL credentials.
- Tests keep H2 MySQL compatibility mode and `create-drop`.
- QueryDSL version is exactly `5.1.0` with Maven `jakarta` classifiers.
- Main-source Q types are generated below `target/generated-sources/annotations`; test-only Q types may be generated below `target/generated-test-sources/test-annotations`. All generated types remain below `target` and are never tracked by Git.
- `JPAQueryFactory` is built from the Spring-managed `EntityManager` proxy.
- Domain repositories extend `JpaRepository` directly.
- No `BaseRepository`, generic custom repository, CRUD service, soft delete, paging abstraction, migration tool, or production sample entity is added.

---

## File Map

- `src/main/java/com/ssafy/upcycle/common/jpa/entity/BaseEntity.java`: relocated audited superclass.
- `src/main/java/com/ssafy/upcycle/common/jpa/config/JpaAuditingConfig.java`: relocated auditing switch.
- `src/main/java/com/ssafy/upcycle/common/jpa/config/QuerydslConfig.java`: shared factory bean.
- `pom.xml`: QueryDSL runtime/processor dependencies and explicit annotation processing.
- `src/test/java/com/ssafy/upcycle/common/jpa/entity/BaseEntityAuditingTests.java`: relocated auditing integration test.
- `src/test/java/com/ssafy/upcycle/common/jpa/config/QuerydslTestEntity.java`: test-only entity for generated Q type and real query execution.
- `src/test/java/com/ssafy/upcycle/common/jpa/config/QuerydslConfigTests.java`: H2 QueryDSL integration test.

---

### Task 1: Reorganize Shared JPA Packages Without Behavior Changes

**Files:**
- Delete: `src/main/java/com/ssafy/upcycle/common/entity/BaseEntity.java`
- Delete: `src/main/java/com/ssafy/upcycle/common/config/JpaAuditingConfig.java`
- Create: `src/main/java/com/ssafy/upcycle/common/jpa/entity/BaseEntity.java`
- Create: `src/main/java/com/ssafy/upcycle/common/jpa/config/JpaAuditingConfig.java`
- Delete: `src/test/java/com/ssafy/upcycle/common/entity/BaseEntityAuditingTests.java`
- Create: `src/test/java/com/ssafy/upcycle/common/jpa/entity/BaseEntityAuditingTests.java`

**Interfaces:**
- Produces: `com.ssafy.upcycle.common.jpa.entity.BaseEntity` with unchanged protected construction and audit getters.
- Produces: `com.ssafy.upcycle.common.jpa.config.JpaAuditingConfig` with `@EnableJpaAuditing`.

- [ ] **Step 1: Move the auditing test first and verify RED**

Move the existing test to `com.ssafy.upcycle.common.jpa.entity` and change its config import to:

```java
import com.ssafy.upcycle.common.jpa.config.JpaAuditingConfig;
```

Keep both existing test cases unchanged:

```java
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
```

Run:

```powershell
.\mvnw.cmd -Dtest=BaseEntityAuditingTests test
```

Expected: test compilation fails because the new `common.jpa` production classes do not exist.

- [ ] **Step 2: Relocate production classes with unchanged annotations and fields**

Create `common.jpa.entity.BaseEntity`:

```java
package com.ssafy.upcycle.common.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

Create `common.jpa.config.JpaAuditingConfig`:

```java
package com.ssafy.upcycle.common.jpa.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
```

Delete the two old production paths after confirming `rg` finds no remaining imports:

```powershell
rg -n "common\.(entity\.BaseEntity|config\.JpaAuditingConfig)" src
```

- [ ] **Step 3: Run focused and context tests**

```powershell
.\mvnw.cmd '-Dtest=BaseEntityAuditingTests,UpcycleApplicationTests' test
```

Expected: both auditing tests and the context test pass.

- [ ] **Step 4: Commit package organization**

```powershell
git add src/main/java/com/ssafy/upcycle/common src/test/java/com/ssafy/upcycle/common
git commit -m "refactor: 공통 JPA 패키지 구조 정리"
```

---

### Task 2: QueryDSL Generation, Factory, and Real Query Verification

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/ssafy/upcycle/common/jpa/config/QuerydslConfig.java`
- Create: `src/test/java/com/ssafy/upcycle/common/jpa/config/QuerydslTestEntity.java`
- Create: `src/test/java/com/ssafy/upcycle/common/jpa/config/QuerydslConfigTests.java`

**Interfaces:**
- Consumes: relocated `BaseEntity` and `JpaAuditingConfig` from Task 1.
- Produces: injectable `JPAQueryFactory` and Maven-generated `QQuerydslTestEntity`.

- [ ] **Step 1: Add a failing QueryDSL integration test before dependencies**

Create the test-only entity:

```java
package com.ssafy.upcycle.common.jpa.config;

import com.ssafy.upcycle.common.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "querydsl_test_entity")
class QuerydslTestEntity extends BaseEntity {
    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private String name;

    protected QuerydslTestEntity() {
    }

    QuerydslTestEntity(String name) {
        this.name = name;
    }

    String getName() {
        return name;
    }
}
```

Create the integration test:

```java
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
```

Run:

```powershell
.\mvnw.cmd -Dtest=QuerydslConfigTests test
```

Expected: test compilation fails because QueryDSL types, generated Q type, and `QuerydslConfig` do not exist.

- [ ] **Step 2: Add exact QueryDSL Maven configuration**

Add to `<properties>`:

```xml
<querydsl.version>5.1.0</querydsl.version>
```

Add dependencies:

```xml
<dependency>
    <groupId>com.querydsl</groupId>
    <artifactId>querydsl-jpa</artifactId>
    <version>${querydsl.version}</version>
    <classifier>jakarta</classifier>
</dependency>
<dependency>
    <groupId>com.querydsl</groupId>
    <artifactId>querydsl-apt</artifactId>
    <version>${querydsl.version}</version>
    <classifier>jakarta</classifier>
    <scope>provided</scope>
</dependency>
```

Add the compiler plugin before the Spring Boot plugin:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <proc>full</proc>
    </configuration>
</plugin>
```

This makes annotation processing explicit on Java 21 while allowing both Lombok and QueryDSL processors already present on the compile classpath.

- [ ] **Step 3: Add the factory configuration**

```java
package com.ssafy.upcycle.common.jpa.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuerydslConfig {
    @Bean
    public JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
        return new JPAQueryFactory(entityManager);
    }
}
```

- [ ] **Step 4: Run clean focused verification**

```powershell
.\mvnw.cmd -Dtest=QuerydslConfigTests clean test
```

Expected: Maven generates `QQuerydslTestEntity`, the integration test persists and queries H2, and the test passes.

- [ ] **Step 5: Verify generated-source and dependency boundaries**

```powershell
Get-ChildItem -Recurse target -Filter 'QQuerydslTestEntity.java'
git ls-files target
.\mvnw.cmd dependency:tree '-Dincludes=com.querydsl:*'
```

Expected:

- Exactly one generated Q test entity exists below `target` (normally `target/generated-test-sources/test-annotations`).
- `git ls-files target` prints nothing.
- QueryDSL JPA is compile scope and QueryDSL APT is provided scope, both version 5.1.0 Jakarta artifacts.

- [ ] **Step 6: Run full Maven verification and inspect scope**

```powershell
.\mvnw.cmd clean verify
git diff --check
git status --short
```

Expected: every pre-existing security, response, exception, package, context, and auditing test plus the QueryDSL integration test passes; Maven builds the executable JAR; only planned files are changed.

- [ ] **Step 7: Commit QueryDSL foundation**

```powershell
git add pom.xml src/main/java/com/ssafy/upcycle/common/jpa/config/QuerydslConfig.java src/test/java/com/ssafy/upcycle/common/jpa/config/QuerydslTestEntity.java src/test/java/com/ssafy/upcycle/common/jpa/config/QuerydslConfigTests.java
git commit -m "feat: QueryDSL JPA 기반 구조 추가"
```
