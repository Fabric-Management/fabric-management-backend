package com.fabricmanagement.product.yarn.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class YarnCatalogueMigrationIT {

  private static final String PRE_MIGRATION_VERSION = "20260826100000";
  private static final String MIGRATION_VERSION = "20260826110000";
  private static final String TEMPLATE_TENANT_ID = "00000000-0000-0000-ffff-000000000001";

  @Container static final PostgreSQLContainer<?> POSTGRES = newPostgres("yarn_catalogue_migration");

  @BeforeAll
  static void migrateSuccessDatabaseToPreChange() {
    migrateTo(POSTGRES, PRE_MIGRATION_VERSION);
  }

  @Test
  void createsRlsCataloguesMovesOnlyEndUsesAndDropsDeadTables() throws SQLException {
    migrateTo(POSTGRES, MIGRATION_VERSION);

    assertThat(tableExists(POSTGRES, "production", "prod_yarn_spinning_system")).isTrue();
    assertThat(tableExists(POSTGRES, "production", "prod_yarn_end_use")).isTrue();
    assertThat(tableExists(POSTGRES, "production", "prod_yarn_test_method")).isTrue();
    assertThat(tableExists(POSTGRES, "production", "prod_yarn_attribute")).isFalse();
    assertThat(tableExists(POSTGRES, "production", "prod_yarn_category")).isFalse();

    assertThat(
            strings(
                POSTGRES,
                "SELECT code FROM production.prod_yarn_end_use "
                    + "WHERE tenant_id = '"
                    + TEMPLATE_TENANT_ID
                    + "'::uuid AND system_defined = TRUE ORDER BY display_order"))
        .containsExactly("SEWING", "KNITTING", "WEAVING", "EMBROIDERY");
    assertThat(
            count(
                POSTGRES,
                """
                SELECT
                  (SELECT count(*) FROM production.prod_yarn_spinning_system
                   WHERE code = 'SPECIALTY')
                  + (SELECT count(*) FROM production.prod_yarn_end_use
                     WHERE code = 'SPECIALTY')
                  + (SELECT count(*) FROM production.prod_yarn_test_method
                     WHERE code = 'SPECIALTY')
                """))
        .isZero();

    assertRls(POSTGRES, "prod_yarn_spinning_system");
    assertRls(POSTGRES, "prod_yarn_end_use");
    assertRls(POSTGRES, "prod_yarn_test_method");
  }

  @Test
  void unknownCategoryAbortsBeforeLegacyDrop() throws SQLException {
    try (PostgreSQLContainer<?> database = newPostgres("yarn_unknown_category")) {
      database.start();
      migrateTo(database, PRE_MIGRATION_VERSION);
      execute(
          database,
          """
          INSERT INTO production.prod_yarn_category (
              tenant_id, uid, category_code, category_name, description, display_order
          ) VALUES (
              '00000000-0000-0000-ffff-000000000001', gen_random_uuid()::varchar,
              'CUSTOM_CATEGORY', 'Custom Category', 'Undisposed manual row', 99
          )
          """);

      assertThatThrownBy(() -> migrateTo(database, MIGRATION_VERSION))
          .isInstanceOf(FlywayException.class)
          .hasStackTraceContaining("unknown category codes")
          .hasStackTraceContaining("CUSTOM_CATEGORY");
      assertThat(tableExists(database, "production", "prod_yarn_category")).isTrue();
    }
  }

  @Test
  void unknownAttributeAbortsBeforeLegacyDrop() throws SQLException {
    try (PostgreSQLContainer<?> database = newPostgres("yarn_unknown_attribute")) {
      database.start();
      migrateTo(database, PRE_MIGRATION_VERSION);
      execute(
          database,
          """
          INSERT INTO production.prod_yarn_attribute (
              tenant_id, uid, attribute_code, attribute_name, attribute_type, unit, description
          ) VALUES (
              '00000000-0000-0000-ffff-000000000001', gen_random_uuid()::varchar,
              'CUSTOM_ATTRIBUTE', 'Custom Attribute', 'PHYSICAL', 'custom',
              'Undisposed manual row'
          )
          """);

      assertThatThrownBy(() -> migrateTo(database, MIGRATION_VERSION))
          .isInstanceOf(FlywayException.class)
          .hasStackTraceContaining("unknown attribute codes")
          .hasStackTraceContaining("CUSTOM_ATTRIBUTE");
      assertThat(tableExists(database, "production", "prod_yarn_attribute")).isTrue();
    }
  }

  private static PostgreSQLContainer<?> newPostgres(String databaseName) {
    return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.2-alpine"))
        .withDatabaseName(databaseName)
        .withUsername("fabric_owner")
        .withPassword("fabric123");
  }

  private static void migrateTo(PostgreSQLContainer<?> database, String target) {
    Flyway.configure()
        .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
        .locations("classpath:db/migration")
        .schemas("common_tenant")
        .defaultSchema("common_tenant")
        .target(target)
        .load()
        .migrate();
  }

  private static void assertRls(PostgreSQLContainer<?> database, String tableName)
      throws SQLException {
    assertThat(
            count(
                database,
                "SELECT count(*) FROM pg_class c "
                    + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                    + "WHERE n.nspname = 'production' AND c.relname = '"
                    + tableName
                    + "' AND c.relrowsecurity AND c.relforcerowsecurity"))
        .isEqualTo(1);
    assertThat(
            count(
                database,
                "SELECT count(*) FROM pg_policy p "
                    + "JOIN pg_class c ON c.oid = p.polrelid "
                    + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                    + "WHERE n.nspname = 'production' AND c.relname = '"
                    + tableName
                    + "' AND p.polname = 'rls_tenant_isolation'"))
        .isEqualTo(1);
  }

  private static boolean tableExists(PostgreSQLContainer<?> database, String schema, String table)
      throws SQLException {
    return count(
            database,
            "SELECT count(*) FROM information_schema.tables WHERE table_schema = '"
                + schema
                + "' AND table_name = '"
                + table
                + "'")
        == 1;
  }

  private static long count(PostgreSQLContainer<?> database, String sql) throws SQLException {
    try (Connection connection = connection(database);
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      result.next();
      return result.getLong(1);
    }
  }

  private static List<String> strings(PostgreSQLContainer<?> database, String sql)
      throws SQLException {
    try (Connection connection = connection(database);
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      List<String> values = new java.util.ArrayList<>();
      while (result.next()) {
        values.add(result.getString(1));
      }
      return List.copyOf(values);
    }
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
