package com.fabricmanagement.product.yarn.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class YarnArticleMigrationIT {

  private static final String MIGRATION_VERSION = "20260831120000";
  private static final List<String> TABLES =
      List.of(
          "prod_yarn_article",
          "prod_yarn_article_composition",
          "prod_yarn_article_structure_component",
          "prod_yarn_article_twist_stage",
          "prod_yarn_article_construction_feature",
          "prod_yarn_article_audit");

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.2-alpine"))
          .withDatabaseName("yarn_article_migration")
          .withUsername("fabric_owner")
          .withPassword("fabric123");

  @BeforeAll
  static void migrateYarnArticleSchema() {
    migrateTo(MIGRATION_VERSION);
  }

  @Test
  void createsExactlySixTenantTablesWithRlsAndRequiredIndexes() throws SQLException {
    for (String table : TABLES) {
      assertThat(tableExists(table)).as(table).isTrue();
      assertThat(rlsEnabled(table)).as(table + " RLS").isTrue();
      assertThat(policyCount(table)).as(table + " policy").isEqualTo(1);
    }
    assertThat(indexDefinition("idx_yarn_article_tenant_canonical_key").toLowerCase())
        .contains("tenant_id", "canonical_key", "canonical_key is not null")
        .doesNotContain("unique");
    assertThat(indexDefinition("uq_yarn_article_audit_spec_version").toLowerCase())
        .contains("unique", "tenant_id", "article_id", "spec_version_to")
        .contains("created", "spec_updated");
    assertThat(uniqueConstraint("prod_yarn_article", "uq_yarn_article_product")).isTrue();
    assertThat(
            uniqueConstraint(
                "prod_yarn_article_structure_component", "uq_yarn_article_component_index"))
        .isTrue();
    assertThat(uniqueConstraint("prod_yarn_article_twist_stage", "uq_yarn_article_twist_sequence"))
        .isTrue();
  }

  @Test
  void layerCountAndCompositionPercentageAreDatabaseBackstops() throws SQLException {
    assertThat(constraintDefinition("ck_yarn_article_layer_count_free").toLowerCase())
        .contains("component_count_system is null")
        .contains("component_count_value is null")
        .contains("component_linear_density_tex is null");
    assertThat(constraintDefinition("ck_yarn_article_composition_percentage").toLowerCase())
        .contains("percentage >");
  }

  @Test
  void childIdentityConstraintsAreDeferredForAtomicSpecReplacement() throws SQLException {
    assertDeferred("uq_yarn_article_composition_fiber");
    assertDeferred("uq_yarn_article_component_index");
    assertDeferred("uq_yarn_article_twist_sequence");
    assertDeferred("uq_yarn_article_feature");
  }

  private static void assertDeferred(String constraintName) throws SQLException {
    assertThat(constraintDefinition(constraintName).toLowerCase())
        .as(constraintName)
        .contains("deferrable initially deferred");
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

  private static boolean tableExists(String table) throws SQLException {
    return count(
            "SELECT count(*) FROM information_schema.tables "
                + "WHERE table_schema = 'production' AND table_name = '"
                + table
                + "'")
        == 1;
  }

  private static boolean rlsEnabled(String table) throws SQLException {
    return count(
            "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                + "WHERE n.nspname = 'production' AND c.relname = '"
                + table
                + "' AND c.relrowsecurity AND c.relforcerowsecurity")
        == 1;
  }

  private static long policyCount(String table) throws SQLException {
    return count(
        "SELECT count(*) FROM pg_policy p JOIN pg_class c ON c.oid = p.polrelid "
            + "JOIN pg_namespace n ON n.oid = c.relnamespace "
            + "WHERE n.nspname = 'production' AND c.relname = '"
            + table
            + "' AND p.polname = 'rls_tenant_isolation'");
  }

  private static boolean uniqueConstraint(String table, String name) throws SQLException {
    return count(
            "SELECT count(*) FROM information_schema.table_constraints "
                + "WHERE table_schema = 'production' AND table_name = '"
                + table
                + "' AND constraint_name = '"
                + name
                + "' AND constraint_type = 'UNIQUE'")
        == 1;
  }

  private static String indexDefinition(String name) throws SQLException {
    return string(
        "SELECT indexdef FROM pg_indexes WHERE schemaname = 'production' AND indexname = '"
            + name
            + "'");
  }

  private static String constraintDefinition(String name) throws SQLException {
    return string(
        "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = '" + name + "'");
  }

  private static long count(String sql) throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      result.next();
      return result.getLong(1);
    }
  }

  private static String string(String sql) throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      result.next();
      return result.getString(1);
    }
  }

  private static Connection connection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }
}
