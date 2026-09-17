package com.ssafy.upcycle.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * User 도메인 엔티티와 schema.sql 이 어긋나는 것을 막는다.
 * 구조와 이유는 {@code MaterialSchemaMappingTests} 와 같다 — 요약하면,
 * 테스트 프로파일이 {@code create-drop} 이라 엔티티가 시키는 대로 테이블이 만들어지므로
 * <b>다른 어떤 테스트도 매핑 어긋남을 잡지 못하고</b> 실제 MySQL 배포 시점에야 드러난다.
 *
 * <p>이 도메인에서 특히 위험한 것은 세 테이블 모두 {@code updated_at} 이 없다는 점이다.
 * 누군가 {@code BaseEntity} 를 상속시키는 순간 운영 기동이 깨지는데, 그걸 잡는 장치가 여기다.
 * {@code wallet_nonce} 는 {@code created_at} 마저 없어 더 취약하다.
 */
class UserSchemaMappingTests {

    @ParameterizedTest
    @MethodSource("userDomainEntities")
    void entityMapsExactlyTheColumnsSchemaSqlCreates(Class<?> entityType) throws Exception {
        String table = entityType.getAnnotation(Table.class).name();

        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:user-mapping-" + table + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE",
                "sa",
                "")) {
            executeSchema(connection, loadSchema());

            assertThat(mappedColumns(entityType))
                    .as("%s must map exactly the columns schema.sql creates for %s",
                            entityType.getSimpleName(), table)
                    .containsExactlyInAnyOrderElementsOf(tableColumns(connection, table));
        }
    }

    @ParameterizedTest
    @MethodSource("userEnumColumns")
    void entityEnumMatchesTheValuesAcceptedBySchemaSql(
            String table, String column, Class<? extends Enum<?>> enumType) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:user-enum-" + table + "-" + column + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE",
                "sa",
                "")) {
            executeSchema(connection, loadSchema());

            assertThat(Arrays.stream(enumType.getEnumConstants()).map(Enum::name))
                    .containsExactlyElementsOf(schemaEnumValues(connection, table, column));
        }
    }

    private static Stream<Class<?>> userDomainEntities() {
        return Stream.of(
                User.class,
                CompanyProfile.class,
                UserWallet.class,
                WalletNonce.class,
                RefreshToken.class,
                ConsumerWishlist.class,
                ConsumerCartItem.class);
    }

    private static Stream<Arguments> userEnumColumns() {
        return Stream.of(
                Arguments.of("users", "role", UserRole.class),
                Arguments.of("users", "status", UserStatus.class));
    }

    private Set<String> mappedColumns(Class<?> entityType) {
        Set<String> columns = new TreeSet<>();
        for (Field field : entityType.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }
            Column column = field.getAnnotation(Column.class);
            assertThat(column)
                    .as("%s.%s must declare @Column so the mapping stays comparable to schema.sql",
                            entityType.getSimpleName(), field.getName())
                    .isNotNull();
            columns.add(column.name().toLowerCase());
        }
        return columns;
    }

    private String loadSchema() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/schema.sql")) {
            assertThat(input).as("src/main/resources/schema.sql must exist").isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void executeSchema(Connection connection, String schema) throws SQLException {
        String h2CompatibleSchema = schema.replaceAll(
                "(?i)\\s+ENGINE=InnoDB\\s+DEFAULT CHARSET=utf8mb4\\s+COLLATE=utf8mb4_0900_ai_ci",
                "");
        try (Statement statement = connection.createStatement()) {
            for (String sql : h2CompatibleSchema.split(";")) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        }
    }

    /**
     * schema.sql 이 만든 컬럼 중 <b>엔티티가 매핑해야 하는 것</b>만 돌려준다.
     *
     * <p>생성 컬럼(`GENERATED ALWAYS AS`)은 제외한다. DB 가 계산하는 값이라 엔티티가
     * 매핑하면 INSERT 가 깨지므로, 매핑되지 않은 것이 정상이다.
     * {@code user_wallet.active_user_id} 가 그 경우다 — 활성 지갑 단일성을 강제하는
     * UNIQUE 를 걸기 위한 컬럼이다.
     */
    private Set<String> tableColumns(Connection connection, String table) throws SQLException {
        Set<String> columns = new TreeSet<>();
        try (ResultSet result = connection.getMetaData().getColumns(null, "public", table, "%")) {
            while (result.next()) {
                if ("YES".equalsIgnoreCase(result.getString("IS_GENERATEDCOLUMN"))) {
                    continue;
                }
                columns.add(result.getString("COLUMN_NAME").toLowerCase());
            }
        }
        assertThat(columns).as("schema.sql must create the %s table", table).isNotEmpty();
        return columns;
    }

    private List<String> schemaEnumValues(Connection connection, String table, String column)
            throws SQLException {
        List<String> values = new ArrayList<>();
        String sql = """
                SELECT enum_value.VALUE_NAME
                FROM INFORMATION_SCHEMA.ENUM_VALUES enum_value
                JOIN INFORMATION_SCHEMA.COLUMNS table_column
                  ON table_column.TABLE_SCHEMA = enum_value.OBJECT_SCHEMA
                 AND table_column.TABLE_NAME = enum_value.OBJECT_NAME
                 AND table_column.ORDINAL_POSITION = CAST(enum_value.ENUM_IDENTIFIER AS INTEGER)
                WHERE LOWER(enum_value.OBJECT_NAME) = LOWER(?)
                  AND LOWER(table_column.COLUMN_NAME) = LOWER(?)
                ORDER BY enum_value.VALUE_ORDINAL
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    values.add(resultSet.getString("VALUE_NAME"));
                }
            }
        }
        assertThat(values).as("schema.sql enum values for %s.%s", table, column).isNotEmpty();
        return values;
    }
}
