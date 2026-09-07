package com.ssafy.upcycle.material.entity;

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
 * 엔티티와 schema.sql 이 어긋나는 것을 막는다.
 *
 * <p>운영 프로파일은 {@code ddl-auto: validate} 라 매핑과 실제 테이블이 다르면 기동 자체가
 * 실패한다. 그런데 테스트 프로파일은 {@code create-drop} 이라 엔티티가 시키는 대로 테이블을
 * 만들어버린다 — 엔티티가 무엇을 선언하든 항상 일치하므로 다른 어떤 테스트도 이 어긋남을
 * 잡지 못하고, 실제 MySQL 에 붙는 배포 시점에야 드러난다.
 *
 * <p>그래서 여기서는 schema.sql 을 H2 에 그대로 올린 뒤 엔티티가 매핑한 컬럼 집합과 대조한다.
 * 엔티티에만 있는 컬럼은 기동 실패의 원인이고, 스키마에만 있는 컬럼은 매핑 누락이다.
 *
 * <p>{@code @DataJpaTest} + {@code ddl-auto=validate} 로 대신할 수는 없다. schema.sql 의
 * {@code ENGINE=InnoDB ...} 구문을 H2 가 받지 못해 스크립트 실행 전에 걷어내야 하기 때문이다
 * ({@code SchemaSqlTests} 도 같은 처리를 한다).
 *
 * <p>Material 도메인 엔티티가 늘어나면 {@link #materialDomainEntities()} 에 한 줄 추가한다.
 */
class MaterialSchemaMappingTests {

    @ParameterizedTest
    @MethodSource("materialDomainEntities")
    void entityMapsExactlyTheColumnsSchemaSqlCreates(Class<?> entityType) throws Exception {
        String table = entityType.getAnnotation(Table.class).name();

        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:material-mapping-" + table + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE",
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
    @MethodSource("materialEnumColumns")
    void entityEnumMatchesTheValuesAcceptedBySchemaSql(
            String table, String column, Class<? extends Enum<?>> enumType) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:material-enum-" + table + "-" + column
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE",
                "sa",
                "")) {
            executeSchema(connection, loadSchema());

            assertThat(Arrays.stream(enumType.getEnumConstants()).map(Enum::name))
                    .containsExactlyElementsOf(schemaEnumValues(connection, table, column));
        }
    }

    private static Stream<Class<?>> materialDomainEntities() {
        return Stream.of(
                Material.class,
                MaterialBalance.class,
                MaterialLock.class,
                MaterialLineage.class);
    }

    private static Stream<Arguments> materialEnumColumns() {
        return Stream.of(
                Arguments.of("material", "root_origin", MaterialRootOrigin.class),
                Arguments.of(
                        "material_lock",
                        "reference_type",
                        MaterialLockReferenceType.class),
                Arguments.of("material_lock", "status", MaterialLockStatus.class));
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
