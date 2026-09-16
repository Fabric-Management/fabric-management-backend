package com.fabricmanagement.sales.salesorder.infra.repository;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.testsupport.PostgresImage;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Executes the actual slice migration and checks PostgreSQL/RLS/immutability, independent of mocks.
 */
@Testcontainers
class OrderCoverEvidencePersistenceIT {
  @Container static final PostgreSQLContainer<?> POSTGRES = PostgresImage.container();

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID OTHER_TENANT = UUID.randomUUID();
  private static final UUID ORDER = UUID.randomUUID();
  private static final UUID CASE = UUID.randomUUID();

  @BeforeAll
  static void migrate() throws Exception {
    try (var connection = owner();
        var sql = connection.createStatement()) {
      sql.execute("CREATE ROLE fabric_app LOGIN PASSWORD 'app_test' NOSUPERUSER NOBYPASSRLS");
      sql.execute("CREATE ROLE fabric_system LOGIN PASSWORD 'system_test' NOSUPERUSER BYPASSRLS");
      sql.execute("CREATE SCHEMA sales_ord");
      // Existing owner table is the migration's only prerequisite.
      sql.execute(
          "CREATE TABLE sales_ord.sales_order (id UUID PRIMARY KEY, tenant_id UUID NOT NULL)");
      sql.execute(
          new ClassPathResource("db/migration/V20260915150000__order_cover_evidence.sql")
              .getContentAsString(StandardCharsets.UTF_8));
      sql.execute("GRANT USAGE ON SCHEMA sales_ord TO fabric_app, fabric_system");
      try (var insert =
          connection.prepareStatement("INSERT INTO sales_ord.sales_order VALUES (?, ?)")) {
        insert.setObject(1, ORDER);
        insert.setObject(2, TENANT);
        insert.executeUpdate();
      }
      try (var insert =
          connection.prepareStatement(
              """
              INSERT INTO sales_ord.order_cover_evidence_stream
                (id, tenant_id, created_at, updated_at, case_id, sales_order_id)
              VALUES (?, ?, now(), now(), ?, ?)
              """)) {
        insert.setObject(1, UUID.randomUUID());
        insert.setObject(2, TENANT);
        insert.setObject(3, CASE);
        insert.setObject(4, ORDER);
        insert.executeUpdate();
      }
      insertEvidence(connection, 1);
    }
  }

  @Test
  void applicationRoleCannotMutateAndCannotSeeAnotherTenantsEvidence() throws Exception {
    try (var connection = app(TENANT);
        var sql = connection.createStatement()) {
      try (var rows = sql.executeQuery("SELECT count(*) FROM sales_ord.order_cover_evidence")) {
        rows.next();
        assertThat(rows.getInt(1)).isEqualTo(1);
      }
      assertThatThrownBy(
              () -> sql.executeUpdate("UPDATE sales_ord.order_cover_evidence SET revision = 99"))
          .isInstanceOf(SQLException.class);
      assertThatThrownBy(() -> sql.executeUpdate("DELETE FROM sales_ord.order_cover_evidence"))
          .isInstanceOf(SQLException.class);
    }
    try (var connection = app(OTHER_TENANT);
        var sql = connection.createStatement();
        var rows = sql.executeQuery("SELECT count(*) FROM sales_ord.order_cover_evidence")) {
      rows.next();
      assertThat(rows.getInt(1)).isZero();
    }
  }

  @Test
  void databaseRejectsMutationEvenForOwnerAndCannotRebindCaseAcrossTenant() throws Exception {
    try (var connection = owner();
        var sql = connection.createStatement()) {
      assertThatThrownBy(
              () -> sql.executeUpdate("UPDATE sales_ord.order_cover_evidence SET revision = 99"))
          .isInstanceOf(SQLException.class);
      assertThatThrownBy(() -> sql.executeUpdate("DELETE FROM sales_ord.order_cover_evidence"))
          .isInstanceOf(SQLException.class);
      try (var insert =
          connection.prepareStatement(
              """
              INSERT INTO sales_ord.order_cover_evidence_stream
                (id, tenant_id, created_at, updated_at, case_id, sales_order_id)
              VALUES (?, ?, now(), now(), ?, ?)
              """)) {
        insert.setObject(1, UUID.randomUUID());
        insert.setObject(2, OTHER_TENANT);
        insert.setObject(3, UUID.randomUUID());
        insert.setObject(4, ORDER);
        assertThatThrownBy(insert::executeUpdate).isInstanceOf(SQLException.class);
      }
    }
  }

  @Test
  void duplicateRevisionIsRejectedWithoutReplacingHistoricalPayload() throws Exception {
    try (var connection = owner()) {
      assertThatThrownBy(() -> insertEvidence(connection, 1)).isInstanceOf(SQLException.class);
      try (var sql = connection.createStatement();
          var rows =
              sql.executeQuery(
                  "SELECT revision, input_fingerprint FROM sales_ord.order_cover_evidence")) {
        rows.next();
        assertThat(rows.getLong(1)).isEqualTo(1);
        assertThat(rows.getString(2)).isEqualTo("a".repeat(64));
      }
    }
  }

  @Test
  void repeatableReadRevisionConflictRequiresFreshTransaction() throws Exception {
    try (var first = owner();
        var second = owner()) {
      first.setAutoCommit(false);
      second.setAutoCommit(false);
      first.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
      second.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
      try (var sql = first.createStatement()) {
        sql.executeQuery("SELECT * FROM sales_ord.order_cover_evidence_stream FOR UPDATE").close();
        sql.executeUpdate(
            "UPDATE sales_ord.order_cover_evidence_stream SET last_revision = last_revision + 1,"
                + " version = version + 1");
      }
      // Establish second's earlier MVCC view before the first commits.
      try (var sql = second.createStatement()) {
        sql.executeQuery("SELECT * FROM sales_ord.order_cover_evidence_stream").close();
      }
      first.commit();
      try (var sql = second.createStatement()) {
        assertThatThrownBy(
                () ->
                    sql.executeQuery(
                        "SELECT * FROM sales_ord.order_cover_evidence_stream FOR UPDATE"))
            .isInstanceOf(SQLException.class)
            .satisfies(
                error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("40001"));
      }
      second.rollback();
      try (var sql = second.createStatement()) {
        sql.executeUpdate(
            "UPDATE sales_ord.order_cover_evidence_stream SET last_revision = last_revision + 1,"
                + " version = version + 1");
        try (var rows =
            sql.executeQuery("SELECT last_revision FROM sales_ord.order_cover_evidence_stream")) {
          rows.next();
          assertThat(rows.getLong(1)).isEqualTo(2);
        }
      }
      second.rollback();
    }
  }

  @Test
  void trustedPurgeRequiresMatchingTenantAndRollsBackWithoutRemovingHistory() throws Exception {
    try (var connection =
        DriverManager.getConnection(POSTGRES.getJdbcUrl(), "fabric_system", "system_test")) {
      connection.setAutoCommit(false);
      try (var sql = connection.createStatement()) {
        sql.execute(
            "select set_config('app.order_cover_evidence_purge_tenant', '"
                + OTHER_TENANT
                + "', true)");
        assertThatThrownBy(() -> sql.executeUpdate("DELETE FROM sales_ord.order_cover_evidence"))
            .isInstanceOf(SQLException.class);
        connection.rollback();
        sql.execute(
            "select set_config('app.order_cover_evidence_purge_tenant', '" + TENANT + "', true)");
        assertThat(sql.executeUpdate("DELETE FROM sales_ord.order_cover_evidence")).isEqualTo(1);
        connection.rollback();
        try (var rows = sql.executeQuery("SELECT count(*) FROM sales_ord.order_cover_evidence")) {
          rows.next();
          assertThat(rows.getInt(1)).isEqualTo(1);
        }
      } finally {
        connection.rollback();
      }
    }
  }

  @Test
  void applicationCannotOpenPurgePathEvenWithSettingAndAccidentalDeleteGrant() throws Exception {
    try (var connection = owner()) {
      connection.setAutoCommit(false);
      try (var sql = connection.createStatement()) {
        sql.execute("GRANT DELETE ON sales_ord.order_cover_evidence TO fabric_app");
        sql.execute("SET LOCAL ROLE fabric_app");
        sql.execute("select set_config('app.current_tenant', '" + TENANT + "', true)");
        sql.execute(
            "select set_config('app.order_cover_evidence_purge_tenant', '" + TENANT + "', true)");
        assertThatThrownBy(() -> sql.executeUpdate("DELETE FROM sales_ord.order_cover_evidence"))
            .isInstanceOf(SQLException.class)
            .satisfies(
                error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("55000"));
      } finally {
        connection.rollback();
      }
    }
  }

  @Test
  void competingFirstScopeInsertUsesActualRepositorySqlAndFreshTransaction() throws Exception {
    UUID newCase = UUID.randomUUID();
    String insert =
        OrderCoverEvidenceStreamRepository.class
            .getMethod(
                "establishScope",
                UUID.class,
                UUID.class,
                UUID.class,
                UUID.class,
                String.class,
                UUID.class)
            .getAnnotation(Query.class)
            .value();
    try (var first = owner();
        var second = owner()) {
      first.setAutoCommit(false);
      second.setAutoCommit(false);
      first.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
      second.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
      try (var query = second.createStatement()) {
        query.executeQuery("SELECT * FROM sales_ord.order_cover_evidence_stream").close();
      }
      var firstJdbc = new NamedParameterJdbcTemplate(new SingleConnectionDataSource(first, true));
      var secondJdbc = new NamedParameterJdbcTemplate(new SingleConnectionDataSource(second, true));
      Map<String, Object> params =
          Map.of(
              "id",
              UUID.randomUUID(),
              "tenantId",
              TENANT,
              "orderId",
              ORDER,
              "caseId",
              newCase,
              "uid",
              "EVID-OCES-" + newCase,
              "actorId",
              UUID.randomUUID());
      assertThat(firstJdbc.update(insert, params)).isEqualTo(1);
      first.commit();
      assertThatThrownBy(() -> secondJdbc.update(insert, params))
          .hasRootCauseInstanceOf(SQLException.class)
          .satisfies(
              error -> {
                Throwable root = error;
                while (root.getCause() != null) root = root.getCause();
                assertThat(((SQLException) root).getSQLState()).isEqualTo("40001");
              });
      second.rollback();
      assertThat(secondJdbc.update(insert, params)).isZero();
      second.rollback();
    } finally {
      try (var connection = owner();
          var delete =
              connection.prepareStatement(
                  "DELETE FROM sales_ord.order_cover_evidence_stream WHERE tenant_id = ? AND"
                      + " case_id = ?")) {
        delete.setObject(1, TENANT);
        delete.setObject(2, newCase);
        delete.executeUpdate();
      }
    }
  }

  private static void insertEvidence(Connection connection, long revision) throws SQLException {
    try (var insert =
        connection.prepareStatement(
"""
INSERT INTO sales_ord.order_cover_evidence
  (id, tenant_id, created_at, updated_at, case_id, sales_order_id, revision, order_version,
   computed_at, input_fingerprint, rule_version, requirements, inputs, lines)
VALUES (?, ?, now(), now(), ?, ?, ?, 0, now(), ?, 'ORDER_COVER_EVIDENCE_V1', '{}', '{}', '[]')
""")) {
      insert.setObject(1, UUID.randomUUID());
      insert.setObject(2, TENANT);
      insert.setObject(3, CASE);
      insert.setObject(4, ORDER);
      insert.setLong(5, revision);
      insert.setString(6, "a".repeat(64));
      insert.executeUpdate();
    }
  }

  private static Connection owner() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  private static Connection app(UUID tenant) throws SQLException {
    var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "fabric_app", "app_test");
    try (var statement =
        connection.prepareStatement("SELECT set_config('app.current_tenant', ?, false)")) {
      statement.setString(1, tenant.toString());
      statement.execute();
    }
    return connection;
  }
}
