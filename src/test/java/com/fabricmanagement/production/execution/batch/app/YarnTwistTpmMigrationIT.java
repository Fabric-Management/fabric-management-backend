package com.fabricmanagement.production.execution.batch.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.production.execution.batch.domain.attributes.TwistConversion;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class YarnTwistTpmMigrationIT {

  private static final String PRE_MIGRATION_VERSION = "20260823120000";
  private static final String MIGRATION_VERSION = "20260823130000";
  private static final String MIGRATION_SCRIPT =
      "db/migration/V20260823130000__backfill_yarn_twist_tpm.sql";
  private static final String LEGACY_BATCH_ID = "aaaaaaaa-0000-4000-8000-000000000011";
  private static final String CANONICAL_BATCH_ID = "aaaaaaaa-0000-4000-8000-000000000012";
  private static final String FIBER_BATCH_ID = "aaaaaaaa-0000-4000-8000-000000000013";
  private static final String SUNSET_GUARD_SQL =
      """
      SELECT count(*)
      FROM production.production_execution_batch
      WHERE product_type = 'YARN'
        AND attributes ? 'yarn_tpi'
        AND NOT attributes ? 'yarn_twist_tpm'
      """;

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.2-alpine"))
          .withDatabaseName("yarn_twist_tpm_migration")
          .withUsername("fabric_owner")
          .withPassword("fabric123");

  @BeforeAll
  static void migrateToPreChangeAndSeedBatches() throws SQLException {
    migrateTo(PRE_MIGRATION_VERSION);
    try (Connection connection = ownerConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          INSERT INTO common_tenant.common_tenant (id, uid, slug, name, status)
          VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'YARN-0C-MIGRATION',
                  'yarn-0c-migration', 'Yarn 0C Migration', 'ACTIVE');

          INSERT INTO production.prod_product (id, tenant_id, uid, product_type, unit)
          VALUES
            ('aaaaaaaa-0000-4000-8000-000000000001',
             'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'YARN-0C-PRODUCT', 'YARN', 'KG'),
            ('aaaaaaaa-0000-4000-8000-000000000002',
             'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'YARN-0C-FIBER', 'FIBER', 'KG');

          INSERT INTO production.production_execution_batch
            (id, tenant_id, uid, product_id, product_type, attributes, batch_code, quantity,
             reserved_quantity, consumed_quantity, waste_quantity, unit, status)
          VALUES
            ('aaaaaaaa-0000-4000-8000-000000000011',
             'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'YARN-0C-LEGACY',
             'aaaaaaaa-0000-4000-8000-000000000001', 'YARN',
             '{"yarn_tpi": 18.5, "yarn_csp": 2800}'::jsonb,
             'YARN-0C-LEGACY', 10, 0, 0, 0, 'KG', 'AVAILABLE'),
            ('aaaaaaaa-0000-4000-8000-000000000012',
             'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'YARN-0C-CANONICAL',
             'aaaaaaaa-0000-4000-8000-000000000001', 'YARN',
             '{"yarn_tpi": 10, "yarn_twist_tpm": 999.99}'::jsonb,
             'YARN-0C-CANONICAL', 10, 0, 0, 0, 'KG', 'AVAILABLE'),
            ('aaaaaaaa-0000-4000-8000-000000000013',
             'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'YARN-0C-FIBER-BATCH',
             'aaaaaaaa-0000-4000-8000-000000000002', 'FIBER',
             '{"yarn_tpi": 18.5}'::jsonb,
             'YARN-0C-FIBER', 10, 0, 0, 0, 'KG', 'AVAILABLE');
          """);
    }
  }

  @Test
  void backfillsLegacyYarnRowsPreservesEvidenceAndIsIdempotent() throws SQLException {
    BigDecimal legacyTpi = new BigDecimal("18.5");
    BigDecimal expectedTpm = TwistConversion.tpiToTpm(legacyTpi);

    migrateTo(MIGRATION_VERSION);

    assertLegacyRow(expectedTpm, legacyTpi);
    assertCanonicalRowIsUnchanged();
    assertNonYarnRowIsUnchanged();
    assertThat(count(SUNSET_GUARD_SQL)).isZero();

    execute(readClasspathScript(MIGRATION_SCRIPT));

    assertLegacyRow(expectedTpm, legacyTpi);
    assertCanonicalRowIsUnchanged();
    assertNonYarnRowIsUnchanged();
    assertThat(count(SUNSET_GUARD_SQL)).isZero();
  }

  private static void assertLegacyRow(BigDecimal expectedTpm, BigDecimal expectedTpi)
      throws SQLException {
    TwistValues values = readTwistValues(LEGACY_BATCH_ID);
    assertThat(values.turnsPerMeter()).isEqualByComparingTo(expectedTpm);
    assertThat(values.turnsPerInch()).isEqualByComparingTo(expectedTpi);
  }

  private static void assertCanonicalRowIsUnchanged() throws SQLException {
    TwistValues values = readTwistValues(CANONICAL_BATCH_ID);
    assertThat(values.turnsPerMeter()).isEqualByComparingTo("999.99");
    assertThat(values.turnsPerInch()).isEqualByComparingTo("10");
  }

  private static void assertNonYarnRowIsUnchanged() throws SQLException {
    TwistValues values = readTwistValues(FIBER_BATCH_ID);
    assertThat(values.turnsPerMeter()).isNull();
    assertThat(values.turnsPerInch()).isEqualByComparingTo("18.5");
  }

  private static TwistValues readTwistValues(String batchId) throws SQLException {
    try (Connection connection = ownerConnection();
        var statement =
            connection.prepareStatement(
                """
                SELECT (attributes ->> 'yarn_twist_tpm')::numeric AS turns_per_meter,
                       (attributes ->> 'yarn_tpi')::numeric AS turns_per_inch
                FROM production.production_execution_batch
                WHERE id = ?::uuid
                """)) {
      statement.setString(1, batchId);
      try (ResultSet result = statement.executeQuery()) {
        assertThat(result.next()).isTrue();
        return new TwistValues(
            result.getBigDecimal("turns_per_meter"), result.getBigDecimal("turns_per_inch"));
      }
    }
  }

  private static long count(String sql) throws SQLException {
    try (Connection connection = ownerConnection();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      result.next();
      return result.getLong(1);
    }
  }

  private static void execute(String sql) throws SQLException {
    try (Connection connection = ownerConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private static String readClasspathScript(String path) {
    try (var input = new ClassPathResource(path).getInputStream()) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException("Could not read SQL script: " + path, exception);
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

  private record TwistValues(BigDecimal turnsPerMeter, BigDecimal turnsPerInch) {}
}
