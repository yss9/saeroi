package com.ssafy.upcycle.blockchain.entity;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 엔티티와 schema.sql 이 어긋나는 것을 막는다. {@code MaterialSchemaMappingTests} 와 같은 방식이다.
 *
 * <p>운영 프로파일은 {@code ddl-auto: validate} 라 매핑과 실제 테이블이 다르면 기동 자체가
 * 실패한다. 그런데 테스트 프로파일은 {@code create-drop} 이라 엔티티가 시키는 대로 테이블을
 * 만들어버려 <b>다른 어떤 테스트도 이 어긋남을 잡지 못한다.</b> 실제 MySQL 에 붙는 배포
 * 시점에야 드러난다.
 *
 * <p>{@code blockchain_transaction} 은 테이블이 먼저 있고 매핑을 나중에 붙인 경우라
 * 이 위험이 특히 크다 — 컬럼 이름을 손으로 옮겨 적었다.
 */
class BlockchainSchemaMappingTests {

    @ParameterizedTest
    @MethodSource("blockchainDomainEntities")
    void entityMapsExactlyTheColumnsSchemaSqlCreates(Class<?> entityType) throws Exception {
        String table = entityType.getAnnotation(Table.class).name();

        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:blockchain-mapping-" + table + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE",
                "sa",
                "")) {
            executeSchema(connection, loadSchema());

            assertThat(mappedColumns(entityType))
                    .as("%s must map exactly the columns schema.sql creates for %s",
                            entityType.getSimpleName(), table)
                    .containsExactlyInAnyOrderElementsOf(tableColumns(connection, table));
        }
    }

    /**
     * ENUM 순서까지 고정한다. 값이 밀리면 {@code SUCCESS} 로 저장된 행을 {@code PENDING}
     * 으로 읽는다 — 이름으로 저장하므로(EnumType.STRING) 실제로는 매핑 실패가 나지만,
     * 값이 빠져 있으면 그 행을 읽는 순간 예외가 되어 조회 전체가 죽는다.
     */
    @ParameterizedTest
    @MethodSource("blockchainEnumColumns")
    void entityEnumMatchesTheValuesAcceptedBySchemaSql(
            String table, String column, Class<? extends Enum<?>> enumType) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:blockchain-enum-" + table + "-" + column
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE",
                "sa",
                "")) {
            executeSchema(connection, loadSchema());

            assertThat(Arrays.stream(enumType.getEnumConstants()).map(Enum::name))
                    .containsExactlyElementsOf(schemaEnumValues(connection, table, column));
        }
    }

    /**
     * 문자열 컬럼의 <b>타입</b>까지 대조한다. 이름만 맞아도 타입이 다르면 기동이 깨진다.
     *
     * <p>2026-09-07 backend/master #10 이 정확히 이렇게 실패했다 —
     * {@code tx_hash} 가 {@code CHAR(66)} 인데 엔티티는 {@code length = 66} 만 적어
     * {@code VARCHAR(66)} 을 기대했다. 위의 컬럼 이름 대조와 ENUM 대조는 둘 다 통과했고,
     * <b>CI 의 테스트 전체가 초록이었는데 배포에서 롤백됐다.</b>
     *
     * <p>{@code String} 필드만 본다. 숫자·시간 타입은 Hibernate 의 기본 매핑이 그대로
     * 들어맞아 실제로 어긋난 적이 없고, 여기서 그 매핑을 다시 구현하면 이 테스트 자체가
     * 틀릴 위험이 더 크다. 반면 문자 타입은 {@code CHAR} · {@code VARCHAR} · {@code TEXT}
     * 를 사람이 골라 적으므로 어긋난다.
     */
    @ParameterizedTest
    @MethodSource("blockchainDomainEntities")
    void stringColumnsDeclareTheSameTypeAsSchemaSql(Class<?> entityType) throws Exception {
        String table = entityType.getAnnotation(Table.class).name();
        String schema = loadSchema();

        for (Field field : entityType.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())
                    || field.isSynthetic()
                    || field.getType() != String.class) {
                continue;
            }
            Column column = field.getAnnotation(Column.class);
            String columnName = column.name().toLowerCase();
            String schemaType = schemaColumnType(schema, table, columnName);

            assertThat(declaredType(column))
                    .as("%s.%s maps %s.%s which schema.sql declares as %s — "
                            + "ddl-auto: validate rejects a type mismatch at startup, "
                            + "so declare columnDefinition when it is not VARCHAR",
                            entityType.getSimpleName(),
                            field.getName(),
                            table,
                            columnName,
                            schemaType)
                    .isEqualTo(schemaType);
        }
    }

    private static Stream<Class<?>> blockchainDomainEntities() {
        return Stream.of(BlockchainTransaction.class);
    }

    /**
     * 엔티티가 선언한 타입. {@code columnDefinition} 이 없으면 Hibernate 는
     * {@code String} 을 {@code VARCHAR} 로 매핑한다 — {@code length} 는 자릿수만 바꾸고
     * 타입은 바꾸지 않는다. 이 사실을 놓친 것이 #10 롤백의 원인이었다.
     */
    private static String declaredType(Column column) {
        String definition = column.columnDefinition();
        return definition.isBlank() ? "VARCHAR" : normalizeType(definition);
    }

    /**
     * {@code schema.sql} 원문에서 컬럼 타입을 읽는다.
     *
     * <p><b>H2 메타데이터를 쓰지 않는다.</b> 위의 다른 검증들은 schema.sql 을 H2 에 실행해
     * 대조하는데, 타입에는 그 방법이 통하지 않는다 — H2 는 {@code MODE=MySQL} 에서도
     * {@code TEXT} 를 {@code CHARACTER VARYING} 으로 만든다. 그러면 {@code error_message}
     * 처럼 올바르게 매핑된 컬럼을 어긋났다고 판정한다 (이 테스트를 처음 쓸 때 실제로 그랬다).
     * 운영 MySQL 이 만들어진 근거는 schema.sql 이므로 그 원문이 기준이다.
     */
    private String schemaColumnType(String schema, String table, String columnName) {
        Matcher tableBody = Pattern.compile(
                        "CREATE\\s+TABLE\\s+" + Pattern.quote(table) + "\\s*\\((.*?)\\n\\)",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                .matcher(schema);
        assertThat(tableBody.find()).as("schema.sql must create the %s table", table).isTrue();

        Matcher columnDefinition = Pattern.compile(
                        "^\\s*" + Pattern.quote(columnName) + "\\s+(\\S+)",
                        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE)
                .matcher(tableBody.group(1));
        assertThat(columnDefinition.find())
                .as("schema.sql must declare %s.%s", table, columnName)
                .isTrue();
        return normalizeType(columnDefinition.group(1));
    }

    /**
     * MySQL 의 문자 타입 이름들을 한 이름으로 모은다. {@code schema.sql} 과
     * {@code columnDefinition} 이 같은 타입을 다르게 적어도 같다고 보게 한다
     * (예: {@code TEXT} / {@code LONGTEXT}).
     */
    private static String normalizeType(String rawType) {
        String bare = rawType.trim().toUpperCase();
        int parenthesis = bare.indexOf('(');
        if (parenthesis >= 0) {
            bare = bare.substring(0, parenthesis).trim();
        }
        return switch (bare) {
            case "CHARACTER", "CHAR" -> "CHAR";
            case "CHARACTER VARYING", "VARCHAR" -> "VARCHAR";
            case "CHARACTER LARGE OBJECT", "CLOB", "TEXT", "LONGTEXT", "MEDIUMTEXT" -> "TEXT";
            default -> bare;
        };
    }

    private static Stream<Arguments> blockchainEnumColumns() {
        return Stream.of(
                Arguments.of(
                        "blockchain_transaction",
                        "reference_type",
                        BlockchainReferenceType.class),
                Arguments.of(
                        "blockchain_transaction",
                        "status",
                        BlockchainTransactionStatus.class));
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
