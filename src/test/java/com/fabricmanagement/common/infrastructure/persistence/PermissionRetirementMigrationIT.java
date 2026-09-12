package com.fabricmanagement.common.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * PERM-CAT-2: upgrade populated legacy data through Flyway, then exercise both scripts twice. The
 * data transformation runs as the container owner. SET LOCAL ROLE probes additionally prove that
 * both scripts reject a non-bypass role and accept a non-superuser BYPASSRLS owner. This does not
 * verify the role configuration of a deployed environment.
 */
@Testcontainers
class PermissionRetirementMigrationIT {
  private static final String PRE_MIGRATION_VERSION = "20260901181000";
  private static final String MIGRATION_VERSION = "20260912120000";
  private static final String MIGRATION =
      "db/migration/V20260912120000__retire_stale_permission_pairs.sql";
  private static final String ROLLBACK =
      "db/rollback/V20260912120000_ROLLBACK__retire_stale_permission_pairs.sql";
  private static final UUID TEMPLATE = UUID.fromString("00000000-0000-0000-ffff-000000000001");
  private static final UUID TENANT_B = UUID.fromString("ca720000-0000-4000-8000-000000000002");
  private static final List<UUID> TENANTS = List.of(TEMPLATE, TENANT_B);
  private static final List<String> TABLES = List.of("permission_template", "permission_override");
  // Independent literal oracle: never derive expected retirement from the production enum or SQL.
  private static final List<String> RETIRED =
      List.of(
          "admin:access",
          "dashboard:view",
          "fiber:approve",
          "flowboard:edit",
          "flowboard:manage",
          "flowboard:view",
          "notifications:view",
          "partners:read",
          "partners:write",
          "projects:read",
          "projects:write",
          "projects:manage",
          "reports:view",
          "reports:export",
          "settings:view",
          "settings:write",
          "settings:manage");
  private static final List<String> KEPT = List.of("sales:read", "fiber:read");
  private static final Timestamp OLD_UPDATED = Timestamp.valueOf("2025-09-12 10:00:00");
  private static final Timestamp OLD_DELETED = Timestamp.valueOf("2025-09-11 10:00:00");
  private static final List<Fixture> FIXTURES = new ArrayList<>();
  private static Map<RowKey, Map<String, Object>> legacy;

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.2-alpine"))
          .withDatabaseName("permission_retirement_migration")
          .withUsername("fabric_owner")
          .withPassword("fabric123");

  @BeforeAll
  static void migrateToPreviousVersionAndSeedLegacyRows() throws SQLException {
    migrateTo(PRE_MIGRATION_VERSION);
    try (Connection connection = ownerConnection()) {
      connection.setAutoCommit(false);
      for (UUID tenant : TENANTS) {
        UUID user = seedIdentity(connection, tenant);
        for (String table : TABLES) {
          for (String pair : RETIRED) {
            for (int state = 1; state <= 4; state++) {
              seedPermission(connection, table, tenant, user, pair, state, true);
            }
          }
          for (String pair : KEPT) {
            for (int state = 1; state <= 4; state++) {
              seedPermission(connection, table, tenant, user, pair, state, false);
            }
          }
        }
      }
      connection.commit();
    }
    legacy = snapshot();
    // Schema expansion adds this nullable column; account for it in old-row comparisons.
    legacy.values().forEach(row -> row.put("retired_by", null));
  }

  @Test
  void retiresAllPairsPreservesPriorStatesAndSupportsRepeatedRollbackAndMigration()
      throws SQLException {
    migrateTo(MIGRATION_VERSION);
    Map<RowKey, Map<String, Object>> first = snapshot();
    assertMigrated(first, legacy);
    assertCounts(first);

    // The login is a superuser, but each probe changes the ACTIVE role before running the script.
    // A guard that incorrectly checks session_user would let the negative probes pass silently.
    for (String script : List.of(MIGRATION, ROLLBACK)) {
      assertExecutionRole(script, false);
      assertExecutionRole(script, true);
    }

    executeScript(MIGRATION);
    assertThat(snapshot()).isEqualTo(first);

    executeScript(ROLLBACK);
    Map<RowKey, Map<String, Object>> restored = snapshot();
    assertRestored(restored, first);

    executeScript(ROLLBACK);
    assertThat(snapshot()).isEqualTo(restored);

    executeScript(MIGRATION);
    Map<RowKey, Map<String, Object>> migratedAgain = snapshot();
    assertMigrated(migratedAgain, restored);
    assertCounts(migratedAgain);
    for (Fixture fixture : FIXTURES) {
      if (fixture.retired() && fixture.state() <= 2) {
        assertLater(migratedAgain.get(fixture.key()), first.get(fixture.key()), "deleted_at");
      }
    }
  }

  private static void assertMigrated(
      Map<RowKey, Map<String, Object>> actual, Map<RowKey, Map<String, Object>> previous) {
    assertThat(actual.keySet()).isEqualTo(legacy.keySet());
    for (Map.Entry<RowKey, Map<String, Object>> entry : legacy.entrySet()) {
      Fixture fixture = fixtureFor(entry.getKey());
      Map<String, Object> row = actual.get(entry.getKey());
      Map<String, Object> expected = new LinkedHashMap<>(entry.getValue());
      if (fixture != null && fixture.changed()) {
        expected.put("is_active", false);
        expected.put("retired_by", marker(fixture.state()));
        if (fixture.state() <= 2) {
          assertThat(row.get("deleted_at")).isNotNull();
          // All updates run in one transaction, so NOW() must stamp these columns equally.
          assertThat(row.get("deleted_at")).isEqualTo(row.get("updated_at"));
          expected.put("deleted_at", row.get("deleted_at"));
        }
        assertLater(row, previous.get(entry.getKey()), "updated_at");
        expected.put("updated_at", row.get("updated_at"));
      }
      assertThat(row).as("migrated %s", entry.getKey()).isEqualTo(expected);
    }
  }

  private static void assertRestored(
      Map<RowKey, Map<String, Object>> actual, Map<RowKey, Map<String, Object>> migrated) {
    assertThat(actual.keySet()).isEqualTo(legacy.keySet());
    for (Map.Entry<RowKey, Map<String, Object>> entry : legacy.entrySet()) {
      Fixture fixture = fixtureFor(entry.getKey());
      Map<String, Object> expected = new LinkedHashMap<>(entry.getValue());
      Map<String, Object> row = actual.get(entry.getKey());
      if (fixture != null && fixture.changed()) {
        assertLater(row, migrated.get(entry.getKey()), "updated_at");
        expected.put("updated_at", row.get("updated_at"));
      }
      assertThat(row).as("restored %s", entry.getKey()).isEqualTo(expected);
    }
  }

  private static void assertCounts(Map<RowKey, Map<String, Object>> rows) {
    for (String table : TABLES) {
      List<Fixture> retired =
          FIXTURES.stream().filter(f -> f.key().table().equals(table) && f.retired()).toList();
      assertThat(retired).hasSize(136);
      assertThat(retired.stream().filter(f -> rows.get(f.key()).get("retired_by") != null).count())
          .as("changed retired rows in %s", table)
          .isEqualTo(102);
      for (UUID tenant : TENANTS) {
        assertThat(
                retired.stream()
                    .filter(f -> f.tenant().equals(tenant))
                    .filter(f -> rows.get(f.key()).get("retired_by") != null)
                    .count())
            .as("changed retired rows in %s / %s", table, tenant)
            .isEqualTo(51);
      }
    }
  }

  private static void assertLater(
      Map<String, Object> newer, Map<String, Object> older, String column) {
    assertThat(((Timestamp) newer.get(column)).toInstant())
        .as("%s advances only on a state change", column)
        .isAfter(((Timestamp) older.get(column)).toInstant());
  }

  private static Fixture fixtureFor(RowKey key) {
    return FIXTURES.stream().filter(f -> f.key().equals(key)).findFirst().orElse(null);
  }

  private static String marker(int state) {
    return switch (state) {
      case 1 -> "PERM-CAT-2";
      case 2 -> "PERM-CAT-2:inactive";
      case 3 -> "PERM-CAT-2:deleted";
      default -> throw new IllegalArgumentException("Unchanged state has no marker: " + state);
    };
  }

  private static UUID seedIdentity(Connection connection, UUID tenant) throws SQLException {
    try (var statement =
        connection.prepareStatement(
            "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, status) VALUES (?, ?, ?,"
                + " 'Permission retirement fixture', 'ACTIVE') ON CONFLICT (id) DO NOTHING")) {
      statement.setObject(1, tenant);
      statement.setString(2, "PC2-" + tenant);
      statement.setString(3, "pc2-" + tenant);
      statement.executeUpdate();
    }
    UUID organization = UUID.randomUUID();
    try (var statement =
        connection.prepareStatement(
            "INSERT INTO common_company.common_organization (id, tenant_id, uid, name, tax_id) "
                + "VALUES (?, ?, ?, 'Permission retirement fixture', ?)")) {
      statement.setObject(1, organization);
      statement.setObject(2, tenant);
      statement.setString(3, "PC2-ORG-" + organization);
      statement.setString(4, organization.toString());
      statement.executeUpdate();
    }
    UUID user = UUID.randomUUID();
    try (var statement =
        connection.prepareStatement(
            "INSERT INTO common_user.common_user "
                + "(id, tenant_id, uid, first_name, last_name, organization_id) "
                + "VALUES (?, ?, ?, 'Permission', 'Fixture', ?)")) {
      statement.setObject(1, user);
      statement.setObject(2, tenant);
      statement.setString(3, "PC2-USER-" + user);
      statement.setObject(4, organization);
      statement.executeUpdate();
    }
    return user;
  }

  private static void seedPermission(
      Connection connection,
      String table,
      UUID tenant,
      UUID user,
      String pair,
      int state,
      boolean retired)
      throws SQLException {
    UUID id = UUID.randomUUID();
    String[] halves = pair.split(":");
    boolean template = table.equals("permission_template");
    String extraColumns = template ? "role_code, department_code" : "user_id, granted_by";
    // table and column names are closed test constants; all fixture values are bound parameters.
    String sql =
        "INSERT INTO common_user."
            + table
            + " (id, tenant_id, uid, resource, action, data_scope, is_active, deleted_at, "
            + "created_at, updated_at, version, "
            + extraColumns
            + ") "
            + "VALUES (?, ?, ?, ?, ?, 'OWN', ?, ?, ?, ?, 7, ?, ?)";
    try (var statement = connection.prepareStatement(sql)) {
      statement.setObject(1, id);
      statement.setObject(2, tenant);
      statement.setString(3, "PC2-" + id);
      statement.setString(4, halves[0]);
      statement.setString(5, halves[1]);
      statement.setBoolean(6, state == 1 || state == 3);
      statement.setTimestamp(7, state >= 3 ? OLD_DELETED : null);
      statement.setTimestamp(8, OLD_UPDATED);
      statement.setTimestamp(9, OLD_UPDATED);
      if (template) {
        statement.setString(10, "PC2_STATE_" + state);
        statement.setString(11, "PC2");
      } else {
        statement.setObject(10, user);
        statement.setObject(11, user);
      }
      statement.executeUpdate();
    }
    FIXTURES.add(new Fixture(new RowKey(table, id), tenant, state, retired));
  }

  private static Map<RowKey, Map<String, Object>> snapshot() throws SQLException {
    Map<RowKey, Map<String, Object>> snapshot = new LinkedHashMap<>();
    try (Connection connection = ownerConnection()) {
      for (String table : TABLES) {
        try (var statement = connection.createStatement();
            ResultSet results = statement.executeQuery("SELECT * FROM common_user." + table)) {
          while (results.next()) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (int column = 1; column <= results.getMetaData().getColumnCount(); column++) {
              values.put(results.getMetaData().getColumnLabel(column), results.getObject(column));
            }
            snapshot.put(new RowKey(table, (UUID) values.get("id")), values);
          }
        }
      }
    }
    return snapshot;
  }

  private static void assertExecutionRole(String path, boolean bypassRls) throws SQLException {
    Map<RowKey, Map<String, Object>> before = snapshot();
    try (Connection connection = ownerConnection();
        var statement = connection.createStatement()) {
      connection.setAutoCommit(false);
      try {
        // Role creation and ownership changes are transactional and always rolled back below.
        // Both probe roles own the tables, so an unrelated DDL permission failure cannot mask RLS.
        statement.execute(
            "CREATE ROLE pc2_execution_role_probe NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE "
                + "NOINHERIT "
                + (bypassRls ? "BYPASSRLS" : "NOBYPASSRLS"));
        statement.execute("GRANT USAGE, CREATE ON SCHEMA common_user TO pc2_execution_role_probe");
        statement.execute(
            "ALTER TABLE common_user.permission_template OWNER TO pc2_execution_role_probe");
        statement.execute(
            "ALTER TABLE common_user.permission_override OWNER TO pc2_execution_role_probe");
        statement.execute("SET LOCAL ROLE pc2_execution_role_probe");
        try (ResultSet role =
            statement.executeQuery(
                "SELECT rolsuper, rolbypassrls FROM pg_catalog.pg_roles WHERE rolname ="
                    + " current_user")) {
          assertThat(role.next()).isTrue();
          assertThat(role.getBoolean("rolsuper")).isFalse();
          assertThat(role.getBoolean("rolbypassrls")).isEqualTo(bypassRls);
        }
        if (bypassRls) {
          statement.execute(readClasspathScript(path));
        } else {
          assertThatThrownBy(() -> statement.execute(readClasspathScript(path)))
              .isInstanceOfSatisfying(
                  SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("42501"))
              .hasMessageContaining("PERM-CAT-2 requires an active SUPERUSER or BYPASSRLS");
        }
      } finally {
        connection.rollback();
      }
    }
    assertThat(snapshot()).as("role probe leaves %s data unchanged", path).isEqualTo(before);
  }

  private static void executeScript(String path) throws SQLException {
    try (Connection connection = ownerConnection();
        var statement = connection.createStatement()) {
      connection.setAutoCommit(false);
      statement.execute(readClasspathScript(path));
      connection.commit();
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
        // Match application.yml: concurrent indexes must not wait on Flyway's own transaction.
        .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
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

  private record RowKey(String table, UUID id) {}

  private record Fixture(RowKey key, UUID tenant, int state, boolean retired) {
    boolean changed() {
      return retired && state != 4;
    }
  }
}
