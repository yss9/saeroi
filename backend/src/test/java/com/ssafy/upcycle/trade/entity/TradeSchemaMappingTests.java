package com.ssafy.upcycle.trade.entity;

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
 * Trade 도메인 엔티티와 schema.sql 이 어긋나는 것을 막는다.
 *
 * <p>운영 프로파일은 {@code ddl-auto: validate} 라 매핑과 실제 테이블이 다르면 기동 자체가
 * 실패한다. 그런데 테스트 프로파일은 {@code create-drop} 이라 엔티티가 시키는 대로 테이블을
 * 만들어버린다 — 엔티티가 무엇을 선언하든 항상 일치하므로 다른 어떤 테스트도 이 어긋남을
 * 잡지 못하고, 실제 MySQL 에 붙는 배포 시점에야 드러난다.
 *
 * <p>Material 쪽에 같은 성격의 테스트가 있으나 도메인별로 따로 둔다. 헬퍼가 겹치지만,
 * 한쪽 도메인의 엔티티 목록 변경이 다른 도메인 테스트 파일을 건드리게 만들지 않는 편이 낫다.
 */
class TradeSchemaMappingTests {

    @ParameterizedTest
    @MethodSource("tradeDomainEntities")
    void entityMapsExactlyTheColumnsSchemaSqlCreates(Class<?> entityType) throws Exception {
        String table = entityType.getAnnotation(Table.class).name();

        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:trade-mapping-" + table + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE",
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
    @MethodSource("tradeEnumColumns")
    void entityEnumMatchesTheValuesAcceptedBySchemaSql(
            String table, String column, Class<? extends Enum<?>> enumType) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:trade-enum-" + table + "-" + column
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE",
                "sa",
                "")) {
            executeSchema(connection, loadSchema());

            assertThat(Arrays.stream(enumType.getEnumConstants()).map(Enum::name))
                    .containsExactlyElementsOf(schemaEnumValues(connection, table, column));
        }
    }

    private static Stream<Class<?>> tradeDomainEntities() {
        return Stream.of(
                MaterialListing.class,
                MaterialTrade.class,
                TradeShipping.class);
    }

    private static Stream<Arguments> tradeEnumColumns() {
        return Stream.of(
                Arguments.of("material_listing", "status", ListingStatus.class),
                Arguments.of("material_trade", "status", TradeStatus.class),
                Arguments.of("material_trade", "payment_method", PaymentMethod.class));
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

    private Set<String> tableColumns(Connection connection, String table) throws SQLException {
        Set<String> columns = new TreeSet<>();
        try (ResultSet result = connection.getMetaData().getColumns(null, "public", table, "%")) {
            while (result.next()) {
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
