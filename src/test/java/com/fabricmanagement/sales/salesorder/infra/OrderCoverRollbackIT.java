package com.fabricmanagement.sales.salesorder.infra;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.testsupport.PostgresImage;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Executes the shipped migration and rollback files in an isolated database for every test. */
@Testcontainers
class OrderCoverRollbackIT {
  @Container static final PostgreSQLContainer<?> POSTGRES = PostgresImage.container();
  private static final String SETTLEMENT = "V20260919100000";
  private static final String LOOKUP = "V20260919100100";
  private static final String BINDING = "V20260919100200";
  private final UUID tenant = UUID.randomUUID();
  private final UUID order = UUID.randomUUID();
  private String database;
  private String url;

  @BeforeEach
  void createPreSliceDatabase() throws Exception {
    database = "cover_" + UUID.randomUUID().toString().replace("-", "");
    try (var connection = containerConnection();
        var sql = connection.createStatement()) {
      sql.execute("CREATE DATABASE " + database);
    }
    url =
        "jdbc:postgresql://"
            + POSTGRES.getHost()
            + ":"
            + POSTGRES.getMappedPort(5432)
            + "/"
            + database;
    try (var connection = owner();
        var sql = connection.createStatement()) {
      sql.execute("CREATE SCHEMA sales_ord; CREATE SCHEMA flowboard; CREATE SCHEMA production");
      // Only pre-slice owner tables are fixtures. All cover tables, constraints, triggers and
      // rollback guards come from the actual resource files, never a test reconstruction.
      sql.execute(
          "CREATE TABLE sales_ord.sales_order(id uuid primary key,tenant_id uuid not null)");
      sql.execute(
          "CREATE TABLE sales_ord.sales_order_line(id uuid primary key,tenant_id uuid not null)");
      sql.execute(
          "CREATE TABLE production.prod_work_order(id uuid primary key,tenant_id uuid not null)");
      sql.execute(
          "CREATE TABLE flowboard.task(id uuid primary key,tenant_id uuid,entity_id uuid,created_at"
              + " timestamptz,task_type text,is_active boolean,closed_at timestamptz)");
      sql.execute(resource("migration", "V20260915150000__order_cover_evidence.sql"));
      sql.execute(resource("migration", "V20260918100000__sales_requirement_profile_v1.sql"));
    }
  }

  @AfterEach
  void dropIsolatedDatabase() throws Exception {
    if (database != null) {
      try (var connection = containerConnection();
          var sql = connection.createStatement()) {
        sql.execute("DROP DATABASE " + database + " WITH (FORCE)");
      }
    }
  }

  @Test
  void migrationBackfillsExistingTenantsWithoutActivatingAnyTenant() throws Exception {
    try (var connection = owner();
        var sql = connection.createStatement()) {
      // FORCE RLS must not turn the global count into an empty tenant-scoped view.
      assertThat(
              scalar(
                  connection,
                  "select count(*) from pg_roles where rolname=current_user and rolsuper"))
          .isEqualTo(1);
      sql.execute("set row_security=off");
      insertOrder(connection);
      sql.executeUpdate(
          "insert into sales_ord.sales_order(id,tenant_id)"
              + " values (gen_random_uuid(),gen_random_uuid())");
      sql.executeUpdate(
          """
          insert into sales_ord.order_cover_evidence_stream
            (id,tenant_id,created_at,updated_at,case_id,sales_order_id)
          select gen_random_uuid(),tenant_id,now(),now(),gen_random_uuid(),id
          from sales_ord.sales_order
          """);

      migrateSlice(connection);

      assertThat(scalar(connection, "select count(distinct tenant_id) from sales_ord.sales_order"))
          .isEqualTo(2);
      assertThat(
              scalar(
                  connection,
                  "select count(distinct tenant_id) from sales_ord.order_cover_case"
                      + " where state='CANCELLED' and closed_at is not null"))
          .isEqualTo(2);
      assertThat(scalar(connection, "select count(*) from sales_ord.order_cover_activation"))
          .isZero();
    }
  }

  @Test
  void compatibleRollbackRemovesOnlyTheSliceAndPreservesPreexistingOrderAndEvidenceSchema()
      throws Exception {
    try (var connection = owner();
        var sql = connection.createStatement()) {
      migrateSlice(connection);
      insertOrder(connection);
      sql.execute(resource("rollback", BINDING + "_ROLLBACK__work_order_requirement_binding.sql"));
      sql.execute(resource("rollback", LOOKUP + "_ROLLBACK__order_cover_flowboard_lookup.sql"));
      sql.execute(resource("rollback", SETTLEMENT + "_ROLLBACK__order_cover_settlement.sql"));
      assertThat(scalar(connection, "select count(*) from sales_ord.sales_order")).isEqualTo(1);
      assertThat(
              scalar(
                  connection,
                  "select count(*) from information_schema.columns where table_schema='sales_ord'"
                      + " and table_name='sales_order' and column_name in"
                      + " ('cover_regime','creation_seq')"))
          .isZero();
      assertThat(
              scalar(
                  connection,
                  "select count(*) from information_schema.columns where table_schema='production'"
                      + " and table_name='prod_work_order' and column_name like"
                      + " 'requirement_profile%'"))
          .isZero();
      assertThat(
              scalar(
                  connection,
                  "select count(*) from pg_class where relname='idx_flowboard_order_cover_active'"))
          .isZero();
      assertThat(scalar(connection, "select count(*) from sales_ord.order_cover_evidence"))
          .isZero();
      assertThat(scalar(connection, "select count(*) from sales_ord.requirement_profile_version"))
          .isZero();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"ACTIVATION", "CASE", "REGIME", "RECEIPT"})
  void incompatibleSettlementRowsBlockRollbackWithoutRemovingData(String kind) throws Exception {
    try (var connection = owner();
        var sql = connection.createStatement()) {
      migrateSlice(connection);
      insertOrder(connection);
      switch (kind) {
        case "ACTIVATION" ->
            sql.executeUpdate(
                """
                insert into sales_ord.order_cover_activation
                  (tenant_id,id,uid,created_at,updated_at,boundary_seq,activated_at,activated_by)
                values ('%s',gen_random_uuid(),'activation',now(),now(),1,now(),gen_random_uuid())
                """
                    .formatted(tenant));
        case "REGIME" ->
            sql.executeUpdate("update sales_ord.sales_order set cover_regime='GOVERNED'");
        case "CASE" -> insertCase(connection);
        case "RECEIPT" -> insertReceipt(connection);
        default -> throw new AssertionError(kind);
      }
      int cases = scalar(connection, "select count(*) from sales_ord.order_cover_case");
      int receipts = scalar(connection, "select count(*) from sales_ord.order_cover_result");
      connection.setAutoCommit(false);
      assertThatThrownBy(
              () ->
                  sql.execute(
                      resource("rollback", SETTLEMENT + "_ROLLBACK__order_cover_settlement.sql")))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("Cannot roll back order-cover settlement");
      connection.rollback();
      assertThat(scalar(connection, "select count(*) from sales_ord.sales_order")).isEqualTo(1);
      assertThat(scalar(connection, "select count(*) from sales_ord.order_cover_case"))
          .isEqualTo(cases);
      assertThat(scalar(connection, "select count(*) from sales_ord.order_cover_result"))
          .isEqualTo(receipts);
      assertThat(
              scalar(
                  connection,
                  "select count(*) from information_schema.columns where table_schema='sales_ord'"
                      + " and table_name='sales_order' and column_name='cover_regime'"))
          .isEqualTo(1);
      if (kind.equals("ACTIVATION"))
        assertThat(scalar(connection, "select count(*) from sales_ord.order_cover_activation"))
            .isEqualTo(1);
      if (kind.equals("REGIME"))
        assertThat(
                scalar(
                    connection,
                    "select count(*) from sales_ord.sales_order where cover_regime='GOVERNED'"))
            .isEqualTo(1);
      connection.rollback();
    }
  }

  @Test
  void boundDraftBlocksBindingRollbackAndRetainsTheOriginalProfile() throws Exception {
    try (var connection = owner();
        var sql = connection.createStatement()) {
      migrateSlice(connection);
      UUID line = UUID.randomUUID(), profile = UUID.randomUUID();
      sql.executeUpdate(
          "insert into sales_ord.sales_order_line(id,tenant_id) values ('"
              + line
              + "','"
              + tenant
              + "')");
      sql.executeUpdate(
          """
          insert into sales_ord.requirement_profile_version
            (id,tenant_id,created_at,updated_at,profile_id,profile_version,sales_order_line_id,fingerprint,snapshot)
          values (gen_random_uuid(),'%s',now(),now(),'%s',1,'%s','%s','{"profileVersion":1}')
          """
              .formatted(tenant, profile, line, "a".repeat(64)));
      sql.executeUpdate(
          """
          insert into production.prod_work_order(id,tenant_id,requirement_profile_id,requirement_profile_version,requirement_profile_snapshot)
          values (gen_random_uuid(),'%s','%s',1,'{"profileVersion":1}')
          """
              .formatted(tenant, profile));
      connection.setAutoCommit(false);
      assertThatThrownBy(
              () ->
                  sql.execute(
                      resource(
                          "rollback", BINDING + "_ROLLBACK__work_order_requirement_binding.sql")))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("Cannot roll back work-order requirement binding");
      connection.rollback();
      assertThat(
              scalar(
                  connection,
                  "select count(*) from production.prod_work_order where"
                      + " requirement_profile_version=1"))
          .isEqualTo(1);
      assertThat(scalar(connection, "select count(*) from sales_ord.requirement_profile_version"))
          .isEqualTo(1);
      connection.rollback();
    }
  }

  private void migrateSlice(Connection connection) throws Exception {
    try (var sql = connection.createStatement()) {
      sql.execute(resource("migration", SETTLEMENT + "__order_cover_settlement.sql"));
      sql.execute(resource("migration", LOOKUP + "__order_cover_flowboard_lookup.sql"));
      sql.execute(resource("migration", BINDING + "__work_order_requirement_binding.sql"));
    }
  }

  private void insertOrder(Connection connection) throws Exception {
    try (var statement =
        connection.prepareStatement(
            "insert into sales_ord.sales_order(id,tenant_id) values (?,?)")) {
      statement.setObject(1, order);
      statement.setObject(2, tenant);
      statement.executeUpdate();
    }
  }

  private UUID insertCase(Connection connection) throws Exception {
    UUID caseId = UUID.randomUUID();
    try (var statement =
        connection.prepareStatement(
            "insert into"
                + " sales_ord.order_cover_case(id,tenant_id,created_at,updated_at,sales_order_id,revision,state)"
                + " values (?,?,now(),now(),?,1,'OPEN')")) {
      statement.setObject(1, caseId);
      statement.setObject(2, tenant);
      statement.setObject(3, order);
      statement.executeUpdate();
    }
    return caseId;
  }

  private void insertReceipt(Connection connection) throws Exception {
    UUID caseId = insertCase(connection), evidenceId = UUID.randomUUID();
    try (var sql = connection.createStatement()) {
      sql.executeUpdate(
          "insert into sales_ord.order_cover_evidence_stream(id,tenant_id,created_at,updated_at,case_id,sales_order_id) values (gen_random_uuid(),'%s',now(),now(),'%s','%s')"
              .formatted(tenant, caseId, order));
      sql.executeUpdate(
          """
          insert into sales_ord.order_cover_evidence
            (id,tenant_id,created_at,updated_at,case_id,sales_order_id,revision,order_version,computed_at,input_fingerprint,rule_version,requirements,inputs,lines)
          values ('%s','%s',now(),now(),'%s','%s',1,0,now(),'%s','V1','{}','{}','[]')
          """
              .formatted(evidenceId, tenant, caseId, order, "a".repeat(64)));
      sql.executeUpdate(
          """
          insert into sales_ord.order_cover_result
            (id,tenant_id,created_at,updated_at,case_id,case_revision,sales_order_id,actor_id,actor_kind,recorded_at,evidence_id,evidence_revision)
          values (gen_random_uuid(),'%s',now(),now(),'%s',1,'%s',gen_random_uuid(),'USER',now(),'%s',1)
          """
              .formatted(tenant, caseId, order, evidenceId));
    }
  }

  private static String resource(String directory, String name) throws Exception {
    return new ClassPathResource("db/" + directory + "/" + name)
        .getContentAsString(StandardCharsets.UTF_8);
  }

  private static int scalar(Connection connection, String query) throws SQLException {
    try (var statement = connection.createStatement();
        var rows = statement.executeQuery(query)) {
      rows.next();
      return rows.getInt(1);
    }
  }

  private Connection owner() throws SQLException {
    return DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  private static Connection containerConnection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }
}
