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
            "material_composition",
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
            "blockchain_transaction");

    @Test
    void schemaCreatesEveryDesignedTableAndConfirmedShippingRelationships() throws Exception {
        String schema = loadSchema();

        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:schema-contract;MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "")) {
            executeSchema(connection, schema);

            assertThat(readTables(connection)).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);
            assertThat(readColumns(connection, "processing_output"))
                    .contains("processing_job_id", "processing_input_id", "grade")
                    .doesNotContain("processing_output_id");
            assertThat(readColumns(connection, "material"))
                    .contains("token_id", "chain_id", "contract_address", "evidence_hash", "manifest_url")
                    .doesNotContain("verification_state");
            assertThat(readColumns(connection, "processing_job"))
                    .contains("brand_reviewed_at", "manifest_url", "payment_method", "payment_amount")
                    .doesNotContain("brand_approved_at", "brand_rejected_at");
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
    void materialLockRejectsZeroAmountEvenAfterConsumption() throws Exception {
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

            assertThatThrownBy(() -> statement.executeUpdate("""
                            INSERT INTO material_lock (
                                material_id, owner_id, amount, purpose, reference_type, reference_id, status
                            ) VALUES (1, 1, 0, 'MARKETPLACE', 'LISTING', 1, 'CONSUMED')
                            """))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void processingOutputCannotReferenceInputFromAnotherJob() throws Exception {
        String schema = loadSchema();

        try (Connection connection = DriverManager.getConnection(
                        "jdbc:h2:mem:processing-output-contract;MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "");
                Statement statement = connection.createStatement()) {
            executeSchema(connection, schema);
            insertProcessingFixture(statement);

            assertThatThrownBy(() -> statement.executeUpdate("""
                            INSERT INTO processing_output (
                                processing_job_id, processing_input_id, output_material_id,
                                material_type, grade, name, quantity
                            ) VALUES (2, 1, NULL, 'DENIM', 'A', 'Output', 100)
                            """))
                    .isInstanceOf(SQLException.class);
        }
    }

    private void insertProcessingFixture(Statement statement) throws SQLException {
        statement.executeUpdate("""
                INSERT INTO users (id, email, password, name, phone, role)
                VALUES
                    (1, 'brand@example.com', 'hash', 'Brand', '010-0000-0001', 'BRAND'),
                    (2, 'processor@example.com', 'hash', 'Processor', '010-0000-0002', 'PROCESSOR')
                """);
        statement.executeUpdate("""
                INSERT INTO material (
                    id, material_code, material_type, root_origin, generation_type,
                    name, total_quantity, registrant_id
                ) VALUES (1, 'MAT-000001', 'DENIM', 'WASTE', 'INITIAL', 'Denim', 30000, 1)
                """);
        statement.executeUpdate("""
                INSERT INTO processing_request (id, request_code, brand_id, processor_id, expires_at)
                VALUES
                    (1, 'REQ-000001', 1, 2, '2099-01-01 00:00:00'),
                    (2, 'REQ-000002', 1, 2, '2099-01-01 00:00:00')
                """);
        statement.executeUpdate("""
                INSERT INTO processing_quote (
                    id, processing_request_id, processor_id, price, lead_days
                ) VALUES
                    (1, 1, 2, 1000, 1),
                    (2, 2, 2, 1000, 1)
                """);
        statement.executeUpdate("""
                INSERT INTO processing_job (
                    id, job_code, processing_request_id, processing_quote_id,
                    brand_id, processor_id, brand_wallet, processor_wallet,
                    fee, payment_method, payment_amount
                ) VALUES
                    (1, 'JOB-000001', 1, 1, 1, 2, '0xbrand', '0xprocessor', 1000, 'MOCK', 1000),
                    (2, 'JOB-000002', 2, 2, 1, 2, '0xbrand', '0xprocessor', 1000, 'MOCK', 1000)
                """);
        statement.executeUpdate("""
                INSERT INTO material_lock (
                    id, material_id, owner_id, amount, purpose, reference_type, reference_id
                ) VALUES
                    (1, 1, 1, 100, 'PROCESSING', 'PROCESSING_JOB', 1),
                    (2, 1, 1, 100, 'PROCESSING', 'PROCESSING_JOB', 2)
                """);
        statement.executeUpdate("""
                INSERT INTO processing_input (
                    id, processing_job_id, material_id, quantity, material_lock_id
                ) VALUES
                    (1, 1, 1, 100, 1),
                    (2, 2, 1, 100, 2)
                """);
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
