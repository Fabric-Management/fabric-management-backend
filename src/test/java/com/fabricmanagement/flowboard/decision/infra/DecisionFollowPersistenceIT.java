package com.fabricmanagement.flowboard.decision.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.testsupport.PostgresImage;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Executes the shipped follow migrations and rollback against an isolated PostgreSQL database. */
@Testcontainers
class DecisionFollowPersistenceIT {
  @Container static final PostgreSQLContainer<?> POSTGRES = PostgresImage.container();
  private static final String MIGRATION = "V20260920100000__decision_follow.sql";
  private static final String BACKFILL = "V20260920100100__decision_follow_backfill.sql";
  private static final String ROLLBACK = "V20260920100000_ROLLBACK__decision_follow.sql";
  private static final UUID SYSTEM = UUID.fromString("00000000-0000-0000-0000-000000000000");
  private final UUID tenant = UUID.randomUUID();
  private final UUID otherTenant = UUID.randomUUID();
  private String database;
  private String url;

  @BeforeAll
  static void createApplicationRole() throws Exception {
    try (var connection = containerConnection();
        var sql = connection.createStatement()) {
      sql.execute(
          """
          DO $$ BEGIN
            IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='fabric_app') THEN
              CREATE ROLE fabric_app LOGIN NOSUPERUSER NOCREATEDB NOBYPASSRLS PASSWORD 'app_test';
            END IF;
          END $$
          """);
    }
  }

  @BeforeEach
  void createDatabase() throws Exception {
    database = "follow_" + UUID.randomUUID().toString().replace("-", "");
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
      sql.execute("CREATE SCHEMA sales_ord; CREATE SCHEMA flowboard; CREATE SCHEMA common_user");
      sql.execute(
          """
          CREATE TABLE common_user.common_user (
            id uuid primary key, tenant_id uuid not null, is_active boolean not null,
            unique (tenant_id,id));
          CREATE TABLE sales_ord.sales_order (
            id uuid primary key, tenant_id uuid not null, created_by uuid,
            unique (tenant_id,id));
          CREATE TABLE sales_ord.order_cover_case (
            id uuid primary key, tenant_id uuid not null, sales_order_id uuid not null,
            task_id uuid, created_at timestamptz not null default now(),
            unique (tenant_id,id));
          CREATE TABLE sales_ord.order_cover_result (
            id uuid primary key, tenant_id uuid not null, case_id uuid not null,
            actor_id uuid not null, actor_kind text not null, recorded_at timestamptz not null);
          CREATE TABLE flowboard.task_assignee (
            id uuid primary key, tenant_id uuid not null, task_id uuid not null,
            user_id uuid, assigned_at timestamptz not null, is_active boolean not null,
            deleted_at timestamptz);
          GRANT USAGE ON SCHEMA flowboard TO fabric_app;
          """);
      sql.execute(resource("migration", MIGRATION));
    }
  }

  @AfterEach
  void dropDatabase() throws Exception {
    if (database == null) return;
    try (var connection = containerConnection();
        var sql = connection.createStatement()) {
      sql.execute("DROP DATABASE " + database + " WITH (FORCE)");
    }
  }

  @Test
  void bothTablesForceTenantRowLevelSecurityThroughTheStandardPolicy() throws Exception {
    try (var connection = owner()) {
      assertThat(
              scalar(
                  connection,
                  """
                  select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace
                  where n.nspname='flowboard'
                    and c.relname in ('decision_follow','decision_follow_suppression')
                    and c.relrowsecurity and c.relforcerowsecurity
                  """))
          .isEqualTo(2);
      assertThat(
              scalar(
                  connection,
                  """
                  select count(*) from pg_policies
                  where schemaname='flowboard'
                    and tablename in ('decision_follow','decision_follow_suppression')
                    and policyname='rls_tenant_isolation'
                    and qual like '%app.current_tenant%'
                    and with_check like '%app.current_tenant%'
                  """))
          .isEqualTo(2);
      assertThat(
              scalar(
                  connection,
                  """
                  select count(*) from information_schema.columns
                  where table_schema='flowboard'
                    and table_name in ('decision_follow','decision_follow_suppression')
                    and column_name in
                      ('uid','created_at','created_by','updated_at','updated_by',
                       'is_active','deleted_at','version')
                  """))
          .isEqualTo(16);
    }
  }

  @Test
  void applicationRoleCannotReadOrInsertAnotherTenantRows() throws Exception {
    UUID caseId = UUID.randomUUID(), userId = UUID.randomUUID();
    UUID otherCase = UUID.randomUUID(), otherUser = UUID.randomUUID();
    try (var connection = owner()) {
      seedCaseAndUser(connection, tenant, caseId, userId, true);
      seedCaseAndUser(connection, otherTenant, otherCase, otherUser, true);
      insertFollow(connection, otherTenant, otherCase, otherUser, "OPENED", null);
    }
    try (var connection = application();
        var sql = connection.createStatement()) {
      sql.execute("select set_config('app.current_tenant','" + tenant + "',false)");
      assertThat(scalar(connection, "select count(*) from flowboard.decision_follow")).isZero();
      assertThatThrownBy(
              () -> insertFollow(connection, otherTenant, otherCase, otherUser, "ASSIGNED", null))
          .isInstanceOf(SQLException.class);
    }
  }

  @Test
  void systemUserIsRejectedByBothTables() throws Exception {
    UUID caseId = UUID.randomUUID(), human = UUID.randomUUID();
    try (var connection = owner();
        var sql = connection.createStatement()) {
      seedCaseAndUser(connection, tenant, caseId, human, true);
      assertThatThrownBy(() -> insertFollow(connection, tenant, caseId, SYSTEM, "OPENED", null))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("chk_decision_follow_human");
      assertThatThrownBy(() -> sql.executeUpdate(suppressionInsert(tenant, caseId, SYSTEM)))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("chk_decision_follow_suppression_human");
    }
  }

  @Test
  void unknownSourceIsRejected() throws Exception {
    UUID caseId = UUID.randomUUID(), userId = UUID.randomUUID();
    try (var connection = owner()) {
      seedCaseAndUser(connection, tenant, caseId, userId, true);
      assertThatThrownBy(() -> insertFollow(connection, tenant, caseId, userId, "IMPLICIT", null))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("chk_decision_follow_source");
    }
  }

  @Test
  void duplicateReasonIsNoOpOnlyWhenConflictHandlingIsRequested() throws Exception {
    UUID caseId = UUID.randomUUID(), userId = UUID.randomUUID();
    try (var connection = owner();
        var sql = connection.createStatement()) {
      seedCaseAndUser(connection, tenant, caseId, userId, true);
      insertFollow(connection, tenant, caseId, userId, "OPENED", null);
      assertThatThrownBy(() -> insertFollow(connection, tenant, caseId, userId, "OPENED", null))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("uq_decision_follow_reason");
      assertThat(
              sql.executeUpdate(
                  followInsert(tenant, caseId, userId, "OPENED", null)
                      + " on conflict (tenant_id,case_id,user_id,source) do nothing"))
          .isZero();
    }
  }

  @Test
  void automaticReasonRejectsMutationOutsideTheTenantPurgePath() throws Exception {
    UUID caseId = UUID.randomUUID(), userId = UUID.randomUUID();
    try (var connection = owner();
        var sql = connection.createStatement()) {
      seedCaseAndUser(connection, tenant, caseId, userId, true);
      insertFollow(connection, tenant, caseId, userId, "OPENED", null);
      assertThatThrownBy(
              () ->
                  sql.executeUpdate(
                      "update flowboard.decision_follow set source_ref=gen_random_uuid() where tenant_id='"
                          + tenant
                          + "'"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("Decision follow reasons are immutable");
      assertThatThrownBy(
              () ->
                  sql.executeUpdate(
                      "delete from flowboard.decision_follow where tenant_id='" + tenant + "'"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("Decision follow reasons are immutable");
    }
  }

  @Test
  void backfillUsesReceiptsAssignmentsIncludingSoftDeletesAndValidCreatorsIdempotently()
      throws Exception {
    UUID caseId = UUID.randomUUID(), taskId = UUID.randomUUID();
    UUID creator = UUID.randomUUID(), assigned = UUID.randomUUID();
    UUID softDeletedAssignee = UUID.randomUUID(), settler = UUID.randomUUID();
    try (var connection = owner();
        var sql = connection.createStatement()) {
      seedCaseAndUser(connection, tenant, caseId, creator, true);
      seedUser(connection, tenant, assigned, true);
      seedUser(connection, tenant, softDeletedAssignee, true);
      seedUser(connection, tenant, settler, false);
      sql.executeUpdate(
          "update sales_ord.order_cover_case set task_id='"
              + taskId
              + "' where id='"
              + caseId
              + "'");
      sql.executeUpdate(
          "insert into sales_ord.order_cover_result values ('"
              + UUID.randomUUID()
              + "','"
              + tenant
              + "','"
              + caseId
              + "','"
              + settler
              + "','USER',now())");
      sql.executeUpdate(assignmentInsert(tenant, taskId, assigned, false));
      sql.executeUpdate(assignmentInsert(tenant, taskId, softDeletedAssignee, true));
      sql.execute(resource("migration", BACKFILL));
      assertThat(scalar(connection, "select count(*) from flowboard.decision_follow")).isEqualTo(4);
      assertThat(
              scalar(
                  connection,
                  "select count(*) from flowboard.decision_follow where source='ASSIGNED'"))
          .isEqualTo(2);
      sql.execute(resource("migration", BACKFILL));
      assertThat(scalar(connection, "select count(*) from flowboard.decision_follow")).isEqualTo(4);
    }
  }

  @Test
  void rollbackDropsTablesWhenOnlyAutomaticReasonsExist() throws Exception {
    UUID caseId = UUID.randomUUID(), userId = UUID.randomUUID();
    try (var connection = owner();
        var sql = connection.createStatement()) {
      seedCaseAndUser(connection, tenant, caseId, userId, true);
      insertFollow(connection, tenant, caseId, userId, "OPENED", null);
      sql.execute(resource("rollback", ROLLBACK));
      assertThat(
              scalar(
                  connection,
                  """
                  select count(*) from information_schema.tables
                  where table_schema='flowboard' and table_name like 'decision_follow%'
                  """))
          .isZero();
    }
  }

  @Test
  void explicitReasonBlocksRollbackWithoutChangingData() throws Exception {
    assertRollbackBlockedByUserDecision(false);
  }

  @Test
  void suppressionBlocksRollbackWithoutChangingData() throws Exception {
    assertRollbackBlockedByUserDecision(true);
  }

  @Test
  void tenantScopedPurgeOrderRemovesFollowRowsWithoutTouchingAnotherTenant() throws Exception {
    UUID caseId = UUID.randomUUID(), userId = UUID.randomUUID();
    UUID otherCase = UUID.randomUUID(), otherUser = UUID.randomUUID();
    try (var connection = owner();
        var sql = connection.createStatement()) {
      seedCaseAndUser(connection, tenant, caseId, userId, true);
      seedCaseAndUser(connection, otherTenant, otherCase, otherUser, true);
      insertFollow(connection, tenant, caseId, userId, "OPENED", null);
      insertFollow(connection, otherTenant, otherCase, otherUser, "OPENED", null);
      sql.executeUpdate(suppressionInsert(tenant, caseId, userId));
      sql.execute("select set_config('app.decision_follow_purge_tenant','" + tenant + "',false)");
      sql.executeUpdate(
          "delete from flowboard.decision_follow_suppression where tenant_id='" + tenant + "'");
      sql.executeUpdate("delete from flowboard.decision_follow where tenant_id='" + tenant + "'");
      sql.executeUpdate("delete from sales_ord.order_cover_case where tenant_id='" + tenant + "'");
      assertThat(scalar(connection, "select count(*) from flowboard.decision_follow")).isOne();
      assertThat(
              scalar(
                  connection,
                  "select count(*) from sales_ord.order_cover_case where tenant_id='"
                      + otherTenant
                      + "'"))
          .isOne();
    }
  }

  private void assertRollbackBlockedByUserDecision(boolean suppression) throws Exception {
    UUID caseId = UUID.randomUUID(), userId = UUID.randomUUID();
    try (var connection = owner();
        var sql = connection.createStatement()) {
      seedCaseAndUser(connection, tenant, caseId, userId, true);
      insertFollow(connection, tenant, caseId, userId, suppression ? "OPENED" : "EXPLICIT", null);
      if (suppression) sql.executeUpdate(suppressionInsert(tenant, caseId, userId));
      connection.setAutoCommit(false);
      assertThatThrownBy(() -> sql.execute(resource("rollback", ROLLBACK)))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining(
              "Cannot roll back decision follow while explicit follows or suppressions exist");
      connection.rollback();
      assertThat(scalar(connection, "select count(*) from flowboard.decision_follow")).isOne();
      assertThat(
              scalar(
                  connection,
                  """
                  select count(*) from information_schema.tables
                  where table_schema='flowboard' and table_name='decision_follow'
                  """))
          .isOne();
    }
  }

  private void seedCaseAndUser(
      Connection connection, UUID tenantId, UUID caseId, UUID userId, boolean active)
      throws SQLException {
    seedUser(connection, tenantId, userId, active);
    UUID orderId = UUID.randomUUID();
    try (var sql = connection.createStatement()) {
      sql.executeUpdate(
          "insert into sales_ord.sales_order values ('"
              + orderId
              + "','"
              + tenantId
              + "','"
              + userId
              + "')");
      sql.executeUpdate(
          "insert into sales_ord.order_cover_case(id,tenant_id,sales_order_id) values ('"
              + caseId
              + "','"
              + tenantId
              + "','"
              + orderId
              + "')");
    }
  }

  private void seedUser(Connection connection, UUID tenantId, UUID userId, boolean active)
      throws SQLException {
    try (var sql = connection.createStatement()) {
      sql.executeUpdate(
          "insert into common_user.common_user values ('"
              + userId
              + "','"
              + tenantId
              + "',"
              + active
              + ")");
    }
  }

  private void insertFollow(
      Connection connection, UUID tenantId, UUID caseId, UUID userId, String source, UUID sourceRef)
      throws SQLException {
    try (var sql = connection.createStatement()) {
      sql.executeUpdate(followInsert(tenantId, caseId, userId, source, sourceRef));
    }
  }

  private static String followInsert(
      UUID tenantId, UUID caseId, UUID userId, String source, UUID sourceRef) {
    return """
        insert into flowboard.decision_follow
          (id,tenant_id,uid,created_at,updated_at,is_active,version,case_id,user_id,source,source_ref)
        values (gen_random_uuid(),'%s',gen_random_uuid()::text,now(),now(),true,0,'%s','%s','%s',%s)
        """
        .formatted(
            tenantId, caseId, userId, source, sourceRef == null ? "null" : "'" + sourceRef + "'");
  }

  private static String suppressionInsert(UUID tenantId, UUID caseId, UUID userId) {
    return """
        insert into flowboard.decision_follow_suppression
          (id,tenant_id,uid,created_at,updated_at,is_active,version,case_id,user_id,suppressed_at)
        values (gen_random_uuid(),'%s',gen_random_uuid()::text,now(),now(),true,0,'%s','%s',now())
        """
        .formatted(tenantId, caseId, userId);
  }

  private static String assignmentInsert(UUID tenantId, UUID taskId, UUID userId, boolean deleted) {
    return """
        insert into flowboard.task_assignee
          (id,tenant_id,task_id,user_id,assigned_at,is_active,deleted_at)
        values (gen_random_uuid(),'%s','%s','%s',now(),%s,%s)
        """
        .formatted(tenantId, taskId, userId, !deleted, deleted ? "now()" : "null");
  }

  private static String resource(String directory, String name) throws Exception {
    return new ClassPathResource("db/" + directory + "/" + name)
        .getContentAsString(StandardCharsets.UTF_8);
  }

  private static int scalar(Connection connection, String query) throws SQLException {
    try (var sql = connection.createStatement();
        var rows = sql.executeQuery(query)) {
      rows.next();
      return rows.getInt(1);
    }
  }

  private Connection owner() throws SQLException {
    return DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  private Connection application() throws SQLException {
    return DriverManager.getConnection(url, "fabric_app", "app_test");
  }

  private static Connection containerConnection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }
}
