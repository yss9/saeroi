package com.ssafy.upcycle.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

/**
 * 활성 지갑 단일성이 <b>실제 DDL 로</b> 강제되는지 확인한다.
 *
 * <p>MySQL 8 은 부분 UNIQUE(`WHERE` 절)를 지원하지 않아 {@code active_user_id} 생성 컬럼에
 * UNIQUE 를 걸었다. 이 트릭은 애플리케이션 테스트로는 검증할 수 없다 — 테스트 프로파일이
 * {@code create-drop} 이라 엔티티가 만드는 테이블에는 그 컬럼이 아예 없기 때문이다.
 * 그래서 {@code UserSchemaMappingTests} 와 같은 방식으로 schema.sql 을 raw H2 에 올려 SQL 을 때린다.
 *
 * <p>이 테스트가 없으면 "생성 컬럼 + UNIQUE" 가 문법만 통과하고 실제로는 아무것도 막지 않는
 * 상태를 알아챌 방법이 없다.
 */
class UserWalletActiveConstraintTests {

    private static final String ADDRESS_A = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String ADDRESS_B = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String ADDRESS_C = "0xcccccccccccccccccccccccccccccccccccccccc";

    @Test
    void schemaAllowsOneActiveWalletAndManyInactiveOnesPerUser() throws Exception {
        try (Connection connection = open("user-wallet-active")) {
            executeSchema(connection);
            insertUser(connection, "owner@example.com");

            insert(connection, 1, ADDRESS_A, false);
            insert(connection, 1, ADDRESS_B, false);
            insert(connection, 1, ADDRESS_C, true);

            assertThat(count(connection, "SELECT COUNT(*) FROM user_wallet WHERE user_id = 1"))
                    .as("이력 보존 — 교체된 지갑도 남는다")
                    .isEqualTo(3);
            assertThat(count(
                            connection,
                            "SELECT COUNT(*) FROM user_wallet WHERE user_id = 1 AND is_active = TRUE"))
                    .isEqualTo(1);
        }
    }

    @Test
    void schemaRejectsASecondActiveWalletForTheSameUser() throws Exception {
        try (Connection connection = open("user-wallet-active-dup")) {
            executeSchema(connection);
            insertUser(connection, "owner@example.com");
            insert(connection, 1, ADDRESS_A, true);

            assertThatThrownBy(() -> insert(connection, 1, ADDRESS_B, true))
                    .as("active_user_id UNIQUE 가 활성 2개를 막아야 한다")
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void differentUsersMayEachHaveTheirOwnActiveWallet() throws Exception {
        try (Connection connection = open("user-wallet-active-multi")) {
            executeSchema(connection);
            insertUser(connection, "a@example.com");
            insertUser(connection, "b@example.com");

            insert(connection, 1, ADDRESS_A, true);
            insert(connection, 2, ADDRESS_B, true);

            assertThat(count(connection, "SELECT COUNT(*) FROM user_wallet WHERE is_active = TRUE"))
                    .as("NULL 은 UNIQUE 에서 충돌하지 않으므로 계정별로 활성 1개가 가능해야 한다")
                    .isEqualTo(2);
        }
    }

    @Test
    void walletAddressStaysGloballyUniqueEvenWhenInactive() throws Exception {
        // 비활성 행도 주소 자리를 점유한다. 그래서 재등록은 새 행이 아니라 되살리기로 처리한다
        // (WalletRegistrationService.register 참고).
        try (Connection connection = open("user-wallet-addr")) {
            executeSchema(connection);
            insertUser(connection, "a@example.com");
            insertUser(connection, "b@example.com");
            insert(connection, 1, ADDRESS_A, false);

            assertThatThrownBy(() -> insert(connection, 2, ADDRESS_A, true))
                    .isInstanceOf(SQLException.class);
        }
    }

    // ── helpers ──────────────────────────────────────────────

    private Connection open(String name) throws SQLException {
        return DriverManager.getConnection(
                "jdbc:h2:mem:" + name + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "");
    }

    private void insertUser(Connection connection, String email) throws SQLException {
        execute(
                connection,
                "INSERT INTO users (email, password, name, phone, role, status) VALUES ('"
                        + email
                        + "', '{noop}x', 'name', '010-0000-0000', 'SUPPLIER', 'ACTIVE')");
    }

    private void insert(Connection connection, long userId, String address, boolean active)
            throws SQLException {
        execute(
                connection,
                "INSERT INTO user_wallet (user_id, wallet_address, chain_id, is_active) VALUES ("
                        + userId
                        + ", '"
                        + address
                        + "', 1337, "
                        + active
                        + ")");
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private long count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private void executeSchema(Connection connection) throws Exception {
        String schema;
        try (InputStream input = getClass().getResourceAsStream("/schema.sql")) {
            assertThat(input).as("src/main/resources/schema.sql must exist").isNotNull();
            schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String h2Compatible = schema.replaceAll(
                "(?i)\\s+ENGINE=InnoDB\\s+DEFAULT CHARSET=utf8mb4\\s+COLLATE=utf8mb4_0900_ai_ci", "");
        try (Statement statement = connection.createStatement()) {
            for (String sql : h2Compatible.split(";")) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        }
    }
}
