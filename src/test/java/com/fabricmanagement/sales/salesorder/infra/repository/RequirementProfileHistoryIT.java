package com.fabricmanagement.sales.salesorder.infra.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.testsupport.PostgresImage;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
abstract class RequirementProfileHistoryIT {

  @Container static final PostgreSQLContainer<?> POSTGRES = PostgresImage.container();

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID OTHER_TENANT = UUID.randomUUID();
  private static final UUID LINE = UUID.randomUUID();
  private static final UUID PROFILE = UUID.randomUUID();

  @BeforeAll
  static void migrate() throws Exception {
    try (var connection = owner();
        var sql = connection.createStatement()) {
      sql.execute("CREATE ROLE fabric_app LOGIN PASSWORD 'app_test' NOSUPERUSER NOBYPASSRLS");
      sql.execute("CREATE ROLE fabric_system LOGIN PASSWORD 'system_test' NOSUPERUSER BYPASSRLS");
      sql.execute("CREATE SCHEMA sales_ord");
      sql.execute("CREATE SCHEMA production");
      sql.execute(
          """
          CREATE TABLE sales_ord.sales_order_line (
            id UUID PRIMARY KEY, tenant_id UUID NOT NULL,
            uid VARCHAR(100), created_at TIMESTAMPTZ, created_by UUID,
            updated_at TIMESTAMPTZ, updated_by UUID, is_active BOOLEAN,
            deleted_at TIMESTAMPTZ, version BIGINT
          )
          """);
      sql.execute(
          """
          CREATE TABLE production.production_execution_batch_certification (
            id UUID PRIMARY KEY, tenant_id UUID NOT NULL
          )
          """);
      sql.execute(
          new ClassPathResource("db/migration/V20260918100000__sales_requirement_profile_v1.sql")
              .getContentAsString(StandardCharsets.UTF_8));
      sql.execute(
          new ClassPathResource("db/migration/V20260918100100__batch_certificate_kind.sql")
              .getContentAsString(StandardCharsets.UTF_8));
      sql.execute("GRANT USAGE ON SCHEMA sales_ord TO fabric_app, fabric_system");
      try (var insert =
          connection.prepareStatement(
              """
              INSERT INTO sales_ord.sales_order_line
                (id, tenant_id, created_at, updated_at, is_active, version)
              VALUES (?, ?, now(), now(), true, 0)
              """)) {
        insert.setObject(1, LINE);
        insert.setObject(2, TENANT);
        insert.executeUpdate();
      }
      insertVersion(connection, 1, "width-150");
      insertVersion(connection, 2, "width-151");
      insertVersion(connection, 3, "width-152");
      sql.executeUpdate(
          """
          UPDATE sales_ord.sales_order_line
          SET requirement_profile_id = '%s', requirement_profile_version = 3,
              requirement_profile_fingerprint = '%s',
              requirement_profile_snapshot = '{"profileVersion":3,"value":"width-152"}'
          WHERE id = '%s'
          """
              .formatted(PROFILE, "c".repeat(64), LINE));
    }
  }

  @Test
  void versionOneRemainsReadableByProfileIdentityAfterTwoChanges() throws Exception {
    try (var connection = app(TENANT);
        var select =
            connection.prepareStatement(
                """
                SELECT snapshot ->> 'value'
                FROM sales_ord.requirement_profile_version
                WHERE profile_id = ? AND profile_version = 1
                """)) {
      select.setObject(1, PROFILE);
      try (var rows = select.executeQuery()) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString(1)).isEqualTo("width-150");
      }
    }
  }

  @Test
  void historyIsImmutableAndTenantIsolated() throws Exception {
    try (var connection = app(TENANT);
        var sql = connection.createStatement()) {
      assertThatThrownBy(
              () ->
                  sql.executeUpdate(
                      "UPDATE sales_ord.requirement_profile_version SET profile_version = 9"))
          .isInstanceOf(SQLException.class);
    }
    try (var connection = app(OTHER_TENANT);
        var sql = connection.createStatement();
        var rows = sql.executeQuery("SELECT count(*) FROM sales_ord.requirement_profile_version")) {
      rows.next();
      assertThat(rows.getInt(1)).isZero();
    }
  }

  @Test
  void currentPointerCannotReferenceAnotherLinesProfileHistory() throws Exception {
    try (var connection = owner();
        var sql = connection.createStatement()) {
      UUID otherLine = UUID.randomUUID();
      sql.executeUpdate(
          "INSERT INTO sales_ord.sales_order_line"
              + " (id, tenant_id, created_at, updated_at, is_active, version) VALUES ('"
              + otherLine
              + "', '"
              + TENANT
              + "', now(), now(), true, 0)");
      assertThatThrownBy(
              () ->
                  sql.executeUpdate(
                      "UPDATE sales_ord.sales_order_line SET requirement_profile_id = '"
                          + PROFILE
                          + "', requirement_profile_version = 1,"
                          + " requirement_profile_fingerprint = '"
                          + "a".repeat(64)
                          + "', requirement_profile_snapshot = '{\"profileVersion\":1}'"
                          + " WHERE id = '"
                          + otherLine
                          + "'"))
          .isInstanceOf(SQLException.class);
    }
  }

  @Test
  void currentProfileColumnsRejectEveryPartialPopulation() throws Exception {
    UUID partialLine = UUID.randomUUID();
    try (var connection = owner();
        var sql = connection.createStatement()) {
      sql.executeUpdate(
          "INSERT INTO sales_ord.sales_order_line"
              + " (id, tenant_id, created_at, updated_at, is_active, version) VALUES ('"
              + partialLine
              + "', '"
              + TENANT
              + "', now(), now(), true, 0)");

      assertThatThrownBy(
              () ->
                  sql.executeUpdate(
                      "UPDATE sales_ord.sales_order_line SET requirement_profile_id = '"
                          + UUID.randomUUID()
                          + "', requirement_profile_version = 1 WHERE id = '"
                          + partialLine
                          + "'"))
          .isInstanceOf(SQLException.class);
    }
  }

  @Test
  void legacyLineAndCertificationRemainUnclassified() throws Exception {
    try (var connection = owner();
        var sql = connection.createStatement()) {
      try (var rows =
          sql.executeQuery(
              "SELECT requirement_profile_id FROM sales_ord.sales_order_line WHERE id = '"
                  + LINE
                  + "'")) {
        rows.next();
        assertThat(rows.getObject(1)).isEqualTo(PROFILE);
      }
      UUID legacyLine = UUID.randomUUID();
      sql.executeUpdate(
          "INSERT INTO sales_ord.sales_order_line"
              + " (id, tenant_id, created_at, updated_at, is_active, version) VALUES ('"
              + legacyLine
              + "', '"
              + TENANT
              + "', now(), now(), true, 0)");
      try (var rows =
          sql.executeQuery(
              "SELECT requirement_profile_id FROM sales_ord.sales_order_line WHERE id = '"
                  + legacyLine
                  + "'")) {
        rows.next();
        assertThat(rows.getObject(1)).isNull();
      }
      UUID certification = UUID.randomUUID();
      sql.executeUpdate(
          "INSERT INTO production.production_execution_batch_certification (id, tenant_id) VALUES ('"
              + certification
              + "', '"
              + TENANT
              + "')");
      try (var rows =
          sql.executeQuery(
              "SELECT certificate_kind FROM production.production_execution_batch_certification WHERE id = '"
                  + certification
                  + "'")) {
        rows.next();
        assertThat(rows.getString(1)).isNull();
      }
    }
  }

  private static void insertVersion(Connection connection, int version, String value)
      throws SQLException {
    try (var insert =
        connection.prepareStatement(
            """
            INSERT INTO sales_ord.requirement_profile_version
              (id, tenant_id, uid, created_at, updated_at, is_active, version,
               profile_id, profile_version, sales_order_line_id, fingerprint, snapshot)
            VALUES (?, ?, ?, now(), now(), true, 0, ?, ?, ?, ?, ?::jsonb)
            """)) {
      insert.setObject(1, UUID.randomUUID());
      insert.setObject(2, TENANT);
      insert.setString(3, "RPF-" + version);
      insert.setObject(4, PROFILE);
      insert.setInt(5, version);
      insert.setObject(6, LINE);
      insert.setString(7, String.valueOf((char) ('a' + version - 1)).repeat(64));
      insert.setString(8, "{\"profileVersion\":" + version + ",\"value\":\"" + value + "\"}");
      insert.executeUpdate();
    }
  }

  private static Connection owner() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  private static Connection app(UUID tenant) throws SQLException {
    Connection connection =
        DriverManager.getConnection(POSTGRES.getJdbcUrl(), "fabric_app", "app_test");
    try (var statement =
        connection.prepareStatement("SELECT set_config('app.current_tenant', ?, false)")) {
      statement.setString(1, tenant.toString());
      statement.execute();
    }
    return connection;
  }
}
