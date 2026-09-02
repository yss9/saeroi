package com.ssafy.upcycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SchemaSqlTests {

    private static final Set<String> EXPECTED_TABLES = Set.of(
            "users",
            "user_wallet",
            "wallet_nonce",
            "company_profile",
            "consumer_address",
            "refresh_token",
            "processor_profile",
            "processor_capability",
            "processor_lead_time",
            "material",
            "material_balance",
            "material_lock",
            "material_lineage",
            "evidence_file",
            "processing_request",
            "processing_request_input",
            "processing_request_type",
            "processing_quote",
            "processing_job",
            "processing_input",
            "processing_output",
            "product_model",
            "production_batch",
            "production_input",
            "production_output",
            "product_item",
            "product_order",
            "product_order_item",
            "product_shipping",
            "canceled_product_order",
            "canceled_product_order_item",
            "material_listing",
            "material_trade",
            "trade_shipping",
            "trade_dispute",
            "blockchain_transaction",
            "point_account",
            "point_transaction");

    @Test
    void schemaCreatesEveryErdTableAndConfirmedShippingRelationships() throws Exception {
        String schema = loadSchema();

        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:schema-contract;MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "")) {
            executeSchema(connection, schema);

            assertThat(readTables(connection)).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);
            assertThat(readColumns(connection, "processing_output"))
                    .contains("processing_job_id")
                    .doesNotContain("processing_output_id");
            assertThat(readColumns(connection, "trade_shipping")).containsExactlyInAnyOrder(
                    "id",
                    "trade_id",
                    "receiver_name",
                    "receiver_phone",
                    "zip_code",
                    "address",
                    "address_detail",
                    "carrier",
                    "tracking_number",
                    "shipped_at",
                    "delivered_at");
            assertThat(importedKeyColumns(connection, "trade_shipping")).contains("trade_id");
            assertThat(uniqueIndexColumns(connection, "trade_shipping")).contains("trade_id");
        }
    }

    @Test
    void consumedMaterialLockCanReachZeroButActiveLockCannot() throws Exception {
        String schema = loadSchema();

        try (Connection connection = DriverManager.getConnection(
                        "jdbc:h2:mem:material-lock-contract;MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "");
                Statement statement = connection.createStatement()) {
            executeSchema(connection, schema);
            statement.executeUpdate("""
                    INSERT INTO users (id, email, password, name, phone, role)
                    VALUES (1, 'seller@example.com', 'hash', 'Seller', '010-0000-0000', 'SUPPLIER')
                    """);
            statement.executeUpdate("""
                    INSERT INTO material (
                        id, material_code, material_type, root_origin, generation_type,
                        name, total_quantity, registrant_id
                    ) VALUES (1, 'MAT-000001', 'DENIM', 'WASTE', 'INITIAL', 'Denim', 30000, 1)
                    """);

            statement.executeUpdate("""
                    INSERT INTO material_lock (
                        material_id, owner_id, amount, purpose, reference_type, reference_id, status
                    ) VALUES (1, 1, 0, 'MARKETPLACE', 'LISTING', 1, 'CONSUMED')
                    """);

            assertThatThrownBy(() -> statement.executeUpdate("""
                            INSERT INTO material_lock (
                                material_id, owner_id, amount, purpose, reference_type, reference_id, status
                            ) VALUES (1, 1, 0, 'MARKETPLACE', 'LISTING', 2, 'ACTIVE')
                            """))
                    .isInstanceOf(SQLException.class);
        }
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

    private Set<String> readTables(Connection connection) throws SQLException {
        Set<String> tables = new HashSet<>();
        try (ResultSet result = connection.getMetaData().getTables(null, "public", "%", new String[] {"BASE TABLE"})) {
            while (result.next()) {
                tables.add(result.getString("TABLE_NAME").toLowerCase());
            }
        }
        return tables;
    }

    private Set<String> readColumns(Connection connection, String table) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (ResultSet result = connection.getMetaData().getColumns(null, "public", table, "%")) {
            while (result.next()) {
                columns.add(result.getString("COLUMN_NAME").toLowerCase());
            }
        }
        return columns;
    }

    private Set<String> importedKeyColumns(Connection connection, String table) throws SQLException {
        Set<String> columns = new HashSet<>();
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getImportedKeys(null, "public", table)) {
            while (result.next()) {
                columns.add(result.getString("FKCOLUMN_NAME").toLowerCase());
            }
        }
        return columns;
    }

    private Set<String> uniqueIndexColumns(Connection connection, String table) throws SQLException {
        Set<String> columns = new HashSet<>();
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet result = metadata.getIndexInfo(null, "public", table, true, false)) {
            while (result.next()) {
                String column = result.getString("COLUMN_NAME");
                if (column != null) {
                    columns.add(column.toLowerCase());
                }
            }
        }
        return columns;
    }
}
