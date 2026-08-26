package com.fabricmanagement.production.quality.result.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class FiberUniformityMigrationIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.2-alpine"))
          .withDatabaseName("fiber_uniformity_migration")
          .withUsername("fabric_owner")
          .withPassword("fabric123");

  @BeforeAll
  static void migrateToPreUniformityAndSeedExistingRows() throws SQLException {
    migrateTo("20260728131000");
    try (Connection connection = ownerConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          INSERT INTO common_tenant.common_tenant (id, uid, slug, name, status)
          VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'FIBER-UNIFORMITY-MIGRATION',
                  'fiber-uniformity-migration', 'Fiber Uniformity Migration', 'ACTIVE');

          INSERT INTO production.prod_fiber_quality_standard
            (id, tenant_id, uid, iso_code_id, standard_name, is_default)
          SELECT 'aaaaaaaa-0000-4000-8000-000000000031',
                 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'FIBER-STANDARD-LEGACY',
                 id, 'Legacy standard', true
          FROM production.prod_fiber_iso_code
          WHERE iso_code = 'CO';

          INSERT INTO production.prod_product (id, tenant_id, uid, product_type, unit)
          VALUES ('aaaaaaaa-0000-4000-8000-000000000002',
                  'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'FIBER-PRODUCT-LEGACY', 'FIBER', 'KG');

          INSERT INTO production.production_execution_batch
            (id, tenant_id, uid, product_id, product_type, batch_code, quantity,
             reserved_quantity, consumed_quantity, waste_quantity, unit, status)
          VALUES ('aaaaaaaa-0000-4000-8000-000000000003',
                  'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'FIBER-BATCH-LEGACY',
                  'aaaaaaaa-0000-4000-8000-000000000002', 'FIBER', 'FIBER-LEGACY',
                  10, 0, 0, 0, 'KG', 'PENDING_QC');

          INSERT INTO production.production_quality_fiber_test_result
            (id, tenant_id, uid, batch_id, test_date, approval_status)
          VALUES ('aaaaaaaa-0000-4000-8000-000000000041',
                  'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'FIBER-TEST-LEGACY',
                  'aaaaaaaa-0000-4000-8000-000000000003', now(), 'PENDING');
          """);
    }
  }

  @Test
  void leavesNewUniformityColumnsNullForExistingRows() throws SQLException {
    migrateTo("20260823120000");

    try (Connection connection = ownerConnection();
        Statement statement = connection.createStatement();
        ResultSet standard =
            statement.executeQuery(
                """
                SELECT uniformity_index_min, uniformity_index_target, uniformity_index_max
                FROM production.prod_fiber_quality_standard
                WHERE id = 'aaaaaaaa-0000-4000-8000-000000000031'
                """)) {
      assertThat(standard.next()).isTrue();
      assertThat(standard.getObject("uniformity_index_min")).isNull();
      assertThat(standard.getObject("uniformity_index_target")).isNull();
      assertThat(standard.getObject("uniformity_index_max")).isNull();
    }

    try (Connection connection = ownerConnection();
        Statement statement = connection.createStatement();
        ResultSet testResult =
            statement.executeQuery(
                """
                SELECT uniformity_index
                FROM production.production_quality_fiber_test_result
                WHERE id = 'aaaaaaaa-0000-4000-8000-000000000041'
                """)) {
      assertThat(testResult.next()).isTrue();
      assertThat(testResult.getObject("uniformity_index")).isNull();
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
