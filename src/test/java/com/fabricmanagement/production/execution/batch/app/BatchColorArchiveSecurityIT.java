package com.fabricmanagement.production.execution.batch.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class BatchColorArchiveSecurityIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.2-alpine"))
          .withDatabaseName("batch_color_archive_security")
          .withUsername("fabric_owner")
          .withPassword("fabric123");

  @BeforeAll
  static void migrateAndSeed() throws SQLException {
    try (Connection connection = ownerConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          "CREATE ROLE fabric_app LOGIN NOSUPERUSER NOCREATEDB NOBYPASSRLS PASSWORD 'app_test'");
      statement.execute(
          "CREATE ROLE fabric_system LOGIN NOSUPERUSER NOCREATEDB BYPASSRLS PASSWORD 'system_test'");
    }

    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .schemas("common_tenant")
        .defaultSchema("common_tenant")
        .load()
        .migrate();

    try (Connection connection = ownerConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          INSERT INTO common_tenant.common_tenant (id, uid, slug, name, status)
          VALUES
            ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'ARCHIVE-A', 'archive-a', 'Archive A', 'ACTIVE'),
            ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'ARCHIVE-B', 'archive-b', 'Archive B', 'ACTIVE')
          """);
      statement.execute(
          """
          INSERT INTO production.production_execution_batch_color_archive
            (tenant_id, source_row_id, batch_id, attribute_definition_id, attribute_code,
             attribute_value, resolved_color_id, prev_is_active, prev_created_at,
             prev_updated_at, prev_version, cutover_version, cutover_at)
          VALUES
            ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', gen_random_uuid(), gen_random_uuid(),
             gen_random_uuid(), 'COLOR', 'NAVY-01', gen_random_uuid(), true, now(), now(), 0,
             'V20260718120000', now()),
            ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', gen_random_uuid(), gen_random_uuid(),
             gen_random_uuid(), 'COLOR', 'NAVY-01', gen_random_uuid(), true, now(), now(), 0,
             'V20260718120000', now())
          """);
    }
  }

  @Test
  void fabricAppSelectIsTenantIsolatedThroughRealRls() throws SQLException {
    try (Connection connection = appConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("SET app.current_tenant = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'");
      try (ResultSet result =
          statement.executeQuery(
              "SELECT count(*) FROM production.production_execution_batch_color_archive")) {
        result.next();
        assertThat(result.getLong(1)).isEqualTo(1);
      }
    }
  }

  @Test
  void fabricAppCannotInsertUpdateOrDeleteArchiveRows() {
    assertPrivilegeDenied(
        """
        INSERT INTO production.production_execution_batch_color_archive
          (tenant_id, source_row_id, batch_id, attribute_definition_id, attribute_code,
           attribute_value, resolved_color_id, prev_is_active, prev_created_at,
           prev_updated_at, prev_version, cutover_version, cutover_at)
        VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', gen_random_uuid(), gen_random_uuid(),
          gen_random_uuid(), 'COLOR', 'NAVY-01', gen_random_uuid(), true, now(), now(), 0,
          'V20260718120000', now())
        """);
    assertPrivilegeDenied(
        "UPDATE production.production_execution_batch_color_archive SET attribute_value = 'X'");
    assertPrivilegeDenied("DELETE FROM production.production_execution_batch_color_archive");
  }

  private static void assertPrivilegeDenied(String sql) {
    assertThatThrownBy(
            () -> {
              try (Connection connection = appConnection();
                  Statement statement = connection.createStatement()) {
                statement.execute(
                    "SET app.current_tenant = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'");
                statement.execute(sql);
              }
            })
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("permission denied");
  }

  private static Connection ownerConnection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  private static Connection appConnection() throws SQLException {
    return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "fabric_app", "app_test");
  }
}
