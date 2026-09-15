package com.fabricmanagement.flowboard.task.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class TaskGovernanceMigrationIT extends AbstractIntegrationTest {

  private static final String PREVIOUS_VERSION = "20260912120000";
  private static final String MIGRATION_VERSION = "20260915120000";
  private static final UUID LEGACY_DEFINITION_ID =
      UUID.fromString("a9ce2df6-f274-4f61-8e53-d28f27472b01");

  @Autowired private JdbcTemplate jdbc;

  @Test
  void installsTaskGovernanceColumnsAndActiveIdentityConstraint() {
    List<String> columns =
        jdbc.queryForList(
            """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = 'flowboard'
              AND table_name = 'task'
              AND column_name IN (
                'generation_key', 'closed_at', 'workflow_definition_id', 'workflow_version'
              )
            ORDER BY column_name
            """,
            String.class);

    assertThat(columns)
        .containsExactly(
            "closed_at", "generation_key", "workflow_definition_id", "workflow_version");
    assertThat(
            jdbc.queryForObject(
                """
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = 'flowboard'
                  AND indexname = 'uq_task_active_generation_key'
                """,
                String.class))
        .contains("UNIQUE", "tenant_id", "generation_key", "closed_at IS NULL");
    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = 'flowboard'
                  AND table_name = 'task'
                  AND column_name IN ('generation_key', 'workflow_definition_id', 'workflow_version')
                  AND is_nullable = 'NO'
                """,
                Integer.class))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                """
                SELECT count(*) FROM flowboard.task
                WHERE status IN ('DONE', 'CANCELLED') AND closed_at IS NULL
                """,
                Integer.class))
        .isZero();
  }

  @Test
  void enablesForcedTenantRlsOnNewTaskTables() {
    Integer governedTables =
        jdbc.queryForObject(
            """
            SELECT count(*)
            FROM pg_class table_ref
            JOIN pg_namespace schema_ref ON schema_ref.oid = table_ref.relnamespace
            WHERE schema_ref.nspname = 'flowboard'
              AND table_ref.relname IN ('task_affected_subject', 'task_transition_attempt')
              AND table_ref.relrowsecurity = TRUE
              AND table_ref.relforcerowsecurity = TRUE
            """,
            Integer.class);

    assertThat(governedTables).isEqualTo(2);
  }

  @Test
  void upgradesPopulatedLegacyRowsWithStableIdentitiesPinsAndTerminalTimes() throws SQLException {
    try (PostgreSQLContainer<?> database = newMigrationDatabase("task_governance_backfill")) {
      database.start();
      migrateTo(database, PREVIOUS_VERSION);
      UUID tenantId = UUID.randomUUID();
      UUID boardId = UUID.randomUUID();
      UUID subjectId = UUID.randomUUID();
      UUID ruleId = UUID.randomUUID();
      seedBoard(database, tenantId, boardId);

      insertLegacyTask(
          database,
          tenantId,
          boardId,
          "00000000-0000-4000-8000-000000000001",
          "M-1",
          "BACKLOG",
          "MANUAL",
          null,
          null,
          null,
          null,
          null);
      insertLegacyTask(
          database,
          tenantId,
          boardId,
          "00000000-0000-4000-8000-000000000002",
          "T-1",
          "BACKLOG",
          "TEMPLATE",
          UUID.randomUUID(),
          "sales_order",
          subjectId,
          null,
          null);
      insertLegacyTask(
          database,
          tenantId,
          boardId,
          "00000000-0000-4000-8000-000000000003",
          "A-1",
          "BACKLOG",
          "AUTOMATION_RULE",
          ruleId,
          "sales_order",
          subjectId,
          null,
          null);
      insertLegacyTask(
          database,
          tenantId,
          boardId,
          "00000000-0000-4000-8000-000000000004",
          "A-2",
          "BACKLOG",
          "AUTOMATION_RULE",
          ruleId,
          null,
          null,
          null,
          null);
      insertLegacyTask(
          database,
          tenantId,
          boardId,
          "00000000-0000-4000-8000-000000000005",
          "D-1",
          "DONE",
          "MANUAL",
          null,
          null,
          null,
          "2026-01-02T10:00:00Z",
          "2026-01-03T10:00:00Z");
      insertLegacyTask(
          database,
          tenantId,
          boardId,
          "00000000-0000-4000-8000-000000000006",
          "C-1",
          "CANCELLED",
          "MANUAL",
          null,
          null,
          null,
          null,
          "2026-01-04T10:00:00Z");

      migrateTo(database, MIGRATION_VERSION);

      assertThat(value(database, "M-1", "generation_key"))
          .isEqualTo("task:legacyManual:00000000-0000-4000-8000-000000000001");
      assertThat(value(database, "T-1", "generation_key"))
          .isEqualTo(
              "subjectType:SALES_ORDER:subjectId:%s:taskType:PLANNING:fulfillmentMode:NONE"
                  .formatted(subjectId));
      assertThat(value(database, "A-1", "generation_key"))
          .isEqualTo(
              "automationRule:%s:subjectType:SALES_ORDER:subjectId:%s:taskType:PLANNING:fulfillmentMode:NONE"
                  .formatted(ruleId, subjectId));
      assertThat(value(database, "A-2", "generation_key"))
          .isEqualTo("task:legacy:00000000-0000-4000-8000-000000000004");
      assertThat(value(database, "M-1", "workflow_definition_id"))
          .isEqualTo(LEGACY_DEFINITION_ID.toString());
      assertThat(value(database, "M-1", "workflow_version")).isEqualTo("1");
      assertThat(value(database, "D-1", "closed_at")).startsWith("2026-01-02 10:00:00");
      assertThat(value(database, "C-1", "closed_at")).startsWith("2026-01-04 10:00:00");
      assertThat(nullableGovernanceColumnCount(database)).isZero();
    }
  }

  @Test
  void migrationNamesConflictingLegacyGenerationIdentities() throws SQLException {
    try (PostgreSQLContainer<?> database = newMigrationDatabase("task_governance_collision")) {
      database.start();
      migrateTo(database, PREVIOUS_VERSION);
      UUID tenantId = UUID.randomUUID();
      UUID boardId = UUID.randomUUID();
      UUID subjectId = UUID.randomUUID();
      seedBoard(database, tenantId, boardId);
      insertLegacyTask(
          database,
          tenantId,
          boardId,
          UUID.randomUUID().toString(),
          "X-1",
          "BACKLOG",
          "TEMPLATE",
          UUID.randomUUID(),
          "SALES_ORDER",
          subjectId,
          null,
          null);
      insertLegacyTask(
          database,
          tenantId,
          boardId,
          UUID.randomUUID().toString(),
          "X-2",
          "BACKLOG",
          "TEMPLATE",
          UUID.randomUUID(),
          "SALES_ORDER",
          subjectId,
          null,
          null);

      assertThatThrownBy(() -> migrateTo(database, MIGRATION_VERSION))
          .isInstanceOf(FlywayException.class)
          .hasStackTraceContaining("active Task generation-key collisions require review")
          .hasStackTraceContaining(tenantId.toString())
          .hasStackTraceContaining(subjectId.toString());
    }
  }

  private static PostgreSQLContainer<?> newMigrationDatabase(String name) {
    return new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
        .withDatabaseName(name)
        .withUsername("fabric_owner")
        .withPassword("fabric123");
  }

  private static void migrateTo(PostgreSQLContainer<?> database, String target) {
    Flyway.configure()
        // Match application.yml: a non-transactional concurrent index must not wait on Flyway's
        // own transactional advisory-lock connection.
        .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
        .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
        .locations("classpath:db/migration")
        .schemas("common_tenant")
        .defaultSchema("common_tenant")
        .target(target)
        .load()
        .migrate();
  }

  private static void seedBoard(PostgreSQLContainer<?> database, UUID tenantId, UUID boardId)
      throws SQLException {
    execute(
        database,
        """
        INSERT INTO flowboard.board (
            id, tenant_id, uid, name, board_type, wip_limit_default, default_view_type,
            is_active, created_at, updated_at, version
        ) VALUES ('%s', '%s', 'migration-board-%s', 'Migration board', 'GLOBAL', 5,
                  'KANBAN', TRUE, NOW(), NOW(), 0)
        """
            .formatted(boardId, tenantId, boardId));
  }

  private static void insertLegacyTask(
      PostgreSQLContainer<?> database,
      UUID tenantId,
      UUID boardId,
      String taskId,
      String taskNumber,
      String status,
      String sourceType,
      UUID sourceId,
      String entityType,
      UUID entityId,
      String completedAt,
      String updatedAt)
      throws SQLException {
    execute(
        database,
        """
        INSERT INTO flowboard.task (
            id, tenant_id, uid, task_number, board_id, title, task_type, module_type,
            priority, status, entity_type, entity_id, source_type, source_id,
            completed_at, is_active, created_at, updated_at, version
        ) VALUES (
            '%s', '%s', 'migration-task-%s', '%s', '%s', 'Legacy task', 'PLANNING',
            'GENERAL', 'MEDIUM', '%s', %s, %s, '%s', %s, %s, TRUE,
            '2026-01-01T10:00:00Z', %s, 0
        )
        """
            .formatted(
                taskId,
                tenantId,
                taskId,
                taskNumber,
                boardId,
                status,
                sqlLiteral(entityType),
                sqlLiteral(entityId),
                sourceType,
                sqlLiteral(sourceId),
                sqlLiteral(completedAt),
                sqlLiteral(updatedAt == null ? "2026-01-01T10:00:00Z" : updatedAt)));
  }

  private static String value(PostgreSQLContainer<?> database, String taskNumber, String column)
      throws SQLException {
    try (Connection connection = connection(database);
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                "SELECT %s::text FROM flowboard.task WHERE task_number = '%s'"
                    .formatted(column, taskNumber))) {
      assertThat(result.next()).isTrue();
      return result.getString(1);
    }
  }

  private static long nullableGovernanceColumnCount(PostgreSQLContainer<?> database)
      throws SQLException {
    try (Connection connection = connection(database);
        Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                """
                SELECT count(*) FROM flowboard.task
                WHERE generation_key IS NULL OR workflow_definition_id IS NULL
                   OR workflow_version IS NULL
                """)) {
      result.next();
      return result.getLong(1);
    }
  }

  private static String sqlLiteral(Object value) {
    return value == null ? "NULL" : "'" + value + "'";
  }

  private static void execute(PostgreSQLContainer<?> database, String sql) throws SQLException {
    try (Connection connection = connection(database);
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  private static Connection connection(PostgreSQLContainer<?> database) throws SQLException {
    return DriverManager.getConnection(
        database.getJdbcUrl(), database.getUsername(), database.getPassword());
  }
}
