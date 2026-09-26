package com.fabricmanagement.flowboard.generator.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.flowboard.generator.domain.catalogue.TaskTemplateCatalogue;
import com.fabricmanagement.testsupport.PostgresImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * TASK-TEMPLATE-TENANCY-1 M1/M2 on a fresh database per test (T-MIG, T-FP): golden ends with the
 * five keyed rows (quote adopted, the others moved from SYSTEM), SYSTEM holds no key, the Java
 * fingerprint equals SQL md5, both rollbacks are verify-only, and drift aborts the migration.
 */
@Testcontainers
class TaskTemplateCatalogMigrationIT {

  @Container static final PostgreSQLContainer<?> POSTGRES = PostgresImage.container();

  private static final String GOLDEN = "00000000-0000-0000-ffff-000000000001";
  private static final String SYSTEM = "00000000-0000-0000-0000-000000000000";
  private static final String BEFORE_CATALOGUE = "20260926100000";

  private String database;
  private String url;

  @BeforeEach
  void createDatabase() throws SQLException {
    database = "catalog_" + UUID.randomUUID().toString().replace("-", "");
    try (Connection connection = containerConnection();
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
  }

  @AfterEach
  void dropDatabase() throws SQLException {
    try (Connection connection = containerConnection();
        var sql = connection.createStatement()) {
      sql.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
    }
  }

  @Test
  void migrationPlacesTheFiveKeyedTemplatesInGolden() throws SQLException {
    migrateTo(null);

    try (Connection connection = owner()) {
      List<String> goldenKeys =
          strings(
              connection,
              "SELECT catalog_key FROM flowboard.task_template WHERE tenant_id = '"
                  + GOLDEN
                  + "' AND catalog_key IS NOT NULL ORDER BY catalog_key");
      assertThat(goldenKeys)
          .containsExactlyInAnyOrder(
              java.util.Arrays.stream(TaskTemplateCatalogue.values())
                  .map(TaskTemplateCatalogue::key)
                  .toArray(String[]::new));
      assertThat(
              scalar(
                  connection,
                  "SELECT count(*) FROM flowboard.task_template WHERE tenant_id = '"
                      + SYSTEM
                      + "' AND catalog_key IS NOT NULL"))
          .isZero();

      // Quote was adopted in golden, not copied: golden has exactly one quote row, and the SYSTEM
      // duplicate is still there, unkeyed and untouched.
      assertThat(
              scalar(
                  connection,
                  "SELECT count(*) FROM flowboard.task_template WHERE tenant_id = '"
                      + GOLDEN
                      + "' AND event_type = 'QuoteSendRequested'"))
          .isEqualTo(1);
      assertThat(
              scalar(
                  connection,
                  "SELECT count(*) FROM flowboard.task_template WHERE tenant_id = '"
                      + SYSTEM
                      + "' AND event_type = 'QuoteSendRequested' AND catalog_key IS NULL"))
          .isEqualTo(1);
      assertThat(
              scalar(
                  connection,
                  "SELECT count(*) FROM flowboard.task_template WHERE tenant_id = '"
                      + GOLDEN
                      + "' AND uid = 'SYS-TMPL-RECIPE-ASSIGN'"
                      + " AND catalog_key = 'WORK_ORDER_RECIPE_ASSIGNMENT_NEEDED__RECIPE_ASSIGNMENT'"))
          .isEqualTo(1);
    }
  }

  @Test
  void javaFingerprintEqualsSqlMd5ForEveryGoldenRow() throws SQLException {
    migrateTo(null);

    try (Connection connection = owner();
        var sql = connection.createStatement();
        var rows =
            sql.executeQuery(
                """
                SELECT catalog_key, title_template, task_type, module_type, default_priority,
                       default_assignee_role, estimated_hours, auto_labels, checklist_template,
                       md5(concat_ws('|', title_template, task_type, module_type, default_priority,
                                     default_assignee_role, estimated_hours, auto_labels,
                                     checklist_template)) AS sql_md5
                  FROM flowboard.task_template
                 WHERE tenant_id = '00000000-0000-0000-ffff-000000000001'
                   AND catalog_key IS NOT NULL
                """)) {
      int seen = 0;
      while (rows.next()) {
        BigDecimal hours = rows.getBigDecimal("estimated_hours");
        String java =
            TaskTemplateCatalogue.fingerprint(
                rows.getString("title_template"),
                rows.getString("task_type"),
                rows.getString("module_type"),
                rows.getString("default_priority"),
                rows.getString("default_assignee_role"),
                hours,
                rows.getString("auto_labels"),
                rows.getString("checklist_template"));
        assertThat(java).as(rows.getString("catalog_key")).isEqualTo(rows.getString("sql_md5"));
        assertThat(java)
            .isEqualTo(
                TaskTemplateCatalogue.byKey(rows.getString("catalog_key"))
                    .orElseThrow()
                    .seedFingerprint());
        seen++;
      }
      assertThat(seen).isEqualTo(TaskTemplateCatalogue.values().length);
    }
  }

  @Test
  void bothRollbacksAreVerifyOnlyAndRepeatable() throws SQLException {
    migrateTo(null);
    String snapshot =
        "SELECT string_agg(id::text || ':' || tenant_id || ':' || coalesce(catalog_key, '-'),"
            + " ',' ORDER BY id) FROM flowboard.task_template";
    String before;
    try (Connection connection = owner()) {
      before = strings(connection, snapshot).getFirst();
    }

    for (int run = 0; run < 2; run++) {
      runScript("db/rollback/V20260926100100_ROLLBACK__task_template_catalog_golden.sql");
      runScript("db/rollback/V20260926100000_ROLLBACK__task_template_catalog_key.sql");
    }

    try (Connection connection = owner()) {
      assertThat(strings(connection, snapshot).getFirst()).isEqualTo(before);
    }
  }

  @Test
  void driftedSystemSourceRowAbortsTheMigration() throws SQLException {
    migrateTo(BEFORE_CATALOGUE);
    try (Connection connection = owner();
        var sql = connection.createStatement()) {
      sql.execute(
          "UPDATE flowboard.task_template SET title_template = 'edited'"
              + " WHERE tenant_id = '"
              + SYSTEM
              + "' AND event_type = 'SalesOrderConfirmed' AND task_type = 'PLANNING'");
    }

    assertThatThrownBy(() -> migrateTo(null)).hasStackTraceContaining("drifted");
  }

  @Test
  void secondGoldenCandidateAbortsTheMigration() throws SQLException {
    migrateTo(BEFORE_CATALOGUE);
    try (Connection connection = owner();
        var sql = connection.createStatement()) {
      sql.execute(
          """
          INSERT INTO flowboard.task_template (
              id, tenant_id, uid, name, event_type, title_template, task_type, module_type,
              default_priority, default_assignee_role, is_active, created_at, updated_at, version)
          VALUES (gen_random_uuid(), '00000000-0000-0000-ffff-000000000001',
                  gen_random_uuid()::varchar, 'Quote send approval', 'QuoteSendRequested',
                  'Quote send approval - {quote.quoteNumber}', 'APPROVAL', 'GENERAL', 'HIGH',
                  'ANY', TRUE, now(), now(), 0)
          """);
    }

    assertThatThrownBy(() -> migrateTo(null)).hasStackTraceContaining("golden candidates");
  }

  private void migrateTo(String target) {
    var configuration =
        Flyway.configure()
            // Match application.yml: concurrent indexes must not wait on Flyway's own transaction.
            .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
            .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .schemas("common_tenant")
            .defaultSchema("common_tenant");
    if (target != null) {
      configuration.target(target);
    }
    configuration.load().migrate();
  }

  private void runScript(String path) throws SQLException {
    String script;
    try (var input = new ClassPathResource(path).getInputStream()) {
      script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Could not read " + path, e);
    }
    try (Connection connection = owner();
        var sql = connection.createStatement()) {
      sql.execute(script);
    }
  }

  private static int scalar(Connection connection, String query) throws SQLException {
    try (var sql = connection.createStatement();
        var rows = sql.executeQuery(query)) {
      rows.next();
      return rows.getInt(1);
    }
  }

  private static List<String> strings(Connection connection, String query) throws SQLException {
    List<String> values = new ArrayList<>();
    try (var sql = connection.createStatement();
        var rows = sql.executeQuery(query)) {
      while (rows.next()) {
        values.add(rows.getString(1));
      }
    }
    return values;
  }

  private Connection owner() throws SQLException {
    return DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  private static Connection containerConnection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }
}
