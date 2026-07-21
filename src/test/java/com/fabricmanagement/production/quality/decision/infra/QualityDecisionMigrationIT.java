package com.fabricmanagement.production.quality.decision.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class QualityDecisionMigrationIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.2-alpine"))
          .withDatabaseName("quality_decision_migration")
          .withUsername("fabric_owner")
          .withPassword("fabric123");

  @BeforeAll
  static void migrateToPreCutoverAndSeedLegacyRows() throws SQLException {
    migrateTo("20260719120000");
    try (Connection connection = ownerConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          INSERT INTO common_tenant.common_tenant (id, uid, slug, name, status)
          VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'QC-MIGRATION',
                  'qc-migration', 'QC Migration', 'ACTIVE');

          INSERT INTO production.prod_product (id, tenant_id, uid, product_type, unit)
          VALUES ('aaaaaaaa-0000-4000-8000-000000000001',
                  'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'QC-PRODUCT', 'FIBER', 'KG');

          INSERT INTO production.production_execution_batch
            (id, tenant_id, uid, product_id, product_type, batch_code, quantity,
             reserved_quantity, consumed_quantity, waste_quantity, unit, status)
          VALUES
            ('aaaaaaaa-0000-4000-8000-000000000011', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
             'QC-BATCH-PENDING', 'aaaaaaaa-0000-4000-8000-000000000001', 'FIBER',
             'QC-PENDING', 10, 0, 0, 0, 'KG', 'PENDING_QC'),
            ('aaaaaaaa-0000-4000-8000-000000000012', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
             'QC-BATCH-AVAILABLE', 'aaaaaaaa-0000-4000-8000-000000000001', 'FIBER',
             'QC-AVAILABLE', 10, 0, 0, 0, 'KG', 'AVAILABLE'),
            ('aaaaaaaa-0000-4000-8000-000000000013', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
             'QC-BATCH-QUARANTINE', 'aaaaaaaa-0000-4000-8000-000000000001', 'FIBER',
             'QC-QUARANTINE', 10, 0, 0, 0, 'KG', 'QUARANTINE'),
            ('aaaaaaaa-0000-4000-8000-000000000014', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
             'QC-BATCH-REJECTED', 'aaaaaaaa-0000-4000-8000-000000000001', 'FIBER',
             'QC-REJECTED', 10, 0, 0, 0, 'KG', 'QC_REJECTED'),
            ('aaaaaaaa-0000-4000-8000-000000000015', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
             'QC-BATCH-UNIT-OVERRIDE', 'aaaaaaaa-0000-4000-8000-000000000001', 'FIBER',
             'QC-UNIT-OVERRIDE', 10, 0, 0, 0, 'KG', 'AVAILABLE');

          INSERT INTO production.stock_unit
            (id, uid, created_at, updated_at, tenant_id, is_active, version, barcode, batch_id,
             package_type, product_type, initial_weight, current_weight, unit, status,
             source_type, source_id, flagged)
          VALUES
            ('aaaaaaaa-0000-4000-8000-000000000021', 'QC-UNIT-PENDING', now(), now(),
             'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', true, 0, 'QC-UNIT-PENDING',
             'aaaaaaaa-0000-4000-8000-000000000011', 'BALE', 'FIBER', 10, 10, 'KG',
             'AVAILABLE', 'GOODS_RECEIPT', gen_random_uuid(), false),
            ('aaaaaaaa-0000-4000-8000-000000000022', 'QC-UNIT-AVAILABLE', now(), now(),
             'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', true, 0, 'QC-UNIT-AVAILABLE',
             'aaaaaaaa-0000-4000-8000-000000000012', 'BALE', 'FIBER', 10, 10, 'KG',
             'AVAILABLE', 'GOODS_RECEIPT', gen_random_uuid(), false),
            ('aaaaaaaa-0000-4000-8000-000000000023', 'QC-UNIT-QUARANTINE-BATCH', now(), now(),
             'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', true, 0, 'QC-UNIT-QUARANTINE-BATCH',
             'aaaaaaaa-0000-4000-8000-000000000013', 'BALE', 'FIBER', 10, 10, 'KG',
             'AVAILABLE', 'GOODS_RECEIPT', gen_random_uuid(), false),
            ('aaaaaaaa-0000-4000-8000-000000000024', 'QC-UNIT-REJECTED', now(), now(),
             'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', true, 0, 'QC-UNIT-REJECTED',
             'aaaaaaaa-0000-4000-8000-000000000014', 'BALE', 'FIBER', 10, 10, 'KG',
             'AVAILABLE', 'GOODS_RECEIPT', gen_random_uuid(), false),
            ('aaaaaaaa-0000-4000-8000-000000000025', 'QC-UNIT-STATUS-OVERRIDE', now(), now(),
             'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', true, 0, 'QC-UNIT-STATUS-OVERRIDE',
             'aaaaaaaa-0000-4000-8000-000000000015', 'BALE', 'FIBER', 10, 10, 'KG',
             'QUARANTINE', 'GOODS_RECEIPT', gen_random_uuid(), false);
          """);
    }
  }

  @Test
  void backfillsDispositionLedgerPopulationAndEnforcesAppendOnlyTables() throws SQLException {
    migrateTo("20260721120000");

    Map<String, String> dispositionByBarcode = new HashMap<>();
    try (Connection connection = ownerConnection();
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT barcode, quality_disposition FROM production.stock_unit ORDER BY barcode")) {
      while (result.next()) {
        dispositionByBarcode.put(result.getString(1), result.getString(2));
      }
    }

    assertThat(dispositionByBarcode)
        .containsEntry("QC-UNIT-PENDING", "PENDING_INSPECTION")
        .containsEntry("QC-UNIT-AVAILABLE", "RELEASED")
        .containsEntry("QC-UNIT-QUARANTINE-BATCH", "QUARANTINED")
        .containsEntry("QC-UNIT-REJECTED", "NONCONFORMING")
        .containsEntry("QC-UNIT-STATUS-OVERRIDE", "QUARANTINED");
    assertThat(
            count(
                "SELECT count(*) FROM production.quality_decision "
                    + "WHERE origin = 'MIGRATION_BACKFILL'"))
        .isEqualTo(4);
    assertThat(
            count(
                "SELECT count(*) FROM production.quality_decision "
                    + "WHERE origin = 'MIGRATION_BACKFILL' AND decision_scope = 'FULL_LOT'"))
        .isEqualTo(4);
    assertThat(count("SELECT count(*) FROM production.quality_decision_unit")).isEqualTo(4);
    assertThat(
            count(
                """
                SELECT count(*)
                FROM production.stock_unit su
                JOIN production.quality_decision_unit qdu ON qdu.stock_unit_id = su.id
                JOIN production.quality_decision qd ON qd.id = qdu.decision_id
                WHERE su.quality_disposition <> 'PENDING_INSPECTION'
                  AND qd.origin = 'MIGRATION_BACKFILL'
                """))
        .isEqualTo(4);

    assertAppendOnly("UPDATE production.quality_decision SET remarks = 'mutated'");
    assertAppendOnly("DELETE FROM production.quality_decision_unit");
  }

  @Test
  void tenantPurgeGuardAllowsOnlyMatchingScopedDeletesAndRollsBackCleanly() throws SQLException {
    migrateTo("20260721125000");

    assertAppendOnly("DELETE FROM production.quality_decision_unit");
    assertAppendOnlyWithPurgeTenant(
        "DELETE FROM production.quality_decision_unit", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    assertAppendOnlyWithPurgeTenant(
        "UPDATE production.quality_decision SET remarks = 'mutated'",
        "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    try (Connection connection = ownerConnection();
        Statement statement = connection.createStatement()) {
      connection.setAutoCommit(false);
      statement.execute(
          "SELECT set_config('app.quality_decision_purge_tenant', "
              + "'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', true)");
      assertThat(statement.executeUpdate("DELETE FROM production.quality_decision_unit"))
          .isEqualTo(4);
      assertThat(statement.executeUpdate("DELETE FROM production.quality_decision")).isEqualTo(4);
      connection.rollback();
    }

    assertThat(count("SELECT count(*) FROM production.quality_decision_unit")).isEqualTo(4);
    assertThat(count("SELECT count(*) FROM production.quality_decision")).isEqualTo(4);
  }

  private static void assertAppendOnlyWithPurgeTenant(String sql, String purgeTenant) {
    assertThatThrownBy(
            () -> {
              try (Connection connection = ownerConnection();
                  Statement statement = connection.createStatement()) {
                connection.setAutoCommit(false);
                statement.execute(
                    "SELECT set_config('app.quality_decision_purge_tenant', '"
                        + purgeTenant
                        + "', true)");
                statement.execute(sql);
              }
            })
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("append-only");
  }

  private static void assertAppendOnly(String sql) {
    assertThatThrownBy(
            () -> {
              try (Connection connection = ownerConnection();
                  Statement statement = connection.createStatement()) {
                statement.execute(sql);
              }
            })
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("append-only");
  }

  private static long count(String sql) throws SQLException {
    try (Connection connection = ownerConnection();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      result.next();
      return result.getLong(1);
    }
  }

  private static void migrateTo(String target) {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .schemas("common_tenant")
        .defaultSchema("common_tenant")
        .target(target)
        .load()
        .migrate();
  }

  private static Connection ownerConnection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }
}
