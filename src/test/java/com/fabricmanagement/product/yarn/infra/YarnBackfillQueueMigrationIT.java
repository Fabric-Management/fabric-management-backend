package com.fabricmanagement.product.yarn.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class YarnBackfillQueueMigrationIT {

  private static final String MIGRATION_VERSION = "20260901120000";
  private static final String TABLE = "prod_yarn_backfill_reconciliation";

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.2-alpine"))
          .withDatabaseName("yarn_backfill_queue_migration")
          .withUsername("fabric_owner")
          .withPassword("fabric123");

  @BeforeAll
  static void migrate() {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .schemas("common_tenant")
        .defaultSchema("common_tenant")
        .target(MIGRATION_VERSION)
        .load()
        .migrate();
  }

  @Test
  void createsRlsQueueWithReasonAndStatusBackstops() throws SQLException {
    assertThat(
            count(
                "SELECT count(*) FROM information_schema.tables "
                    + "WHERE table_schema='production' AND table_name='"
                    + TABLE
                    + "'"))
        .isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace "
                    + "WHERE n.nspname='production' AND c.relname='"
                    + TABLE
                    + "' AND c.relrowsecurity AND c.relforcerowsecurity"))
        .isEqualTo(1);
    assertThat(constraint("ck_yarn_backfill_reason").toLowerCase())
        .contains("ambiguous", "overlength");
    assertThat(constraint("ck_yarn_backfill_status").toLowerCase()).contains("open", "resolved");
  }

  @Test
  void openRowsAreUniquePerTenantAndProductOnlyWhileOpen() throws SQLException {
    String index =
        string(
            "SELECT indexdef FROM pg_indexes WHERE schemaname='production' "
                + "AND indexname='uq_yarn_backfill_open_product'");
    assertThat(index.toLowerCase())
        .contains("unique", "tenant_id", "product_id")
        .contains("status", "open");

    UUID tenantId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    UUID articleId = UUID.randomUUID();
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute("SET app.current_tenant = '" + tenantId + "'");
      statement.execute(
          "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, status) VALUES ('"
              + tenantId
              + "', 'YBF-MIG-"
              + tenantId
              + "', 'ybf-mig-"
              + tenantId
              + "', 'Yarn backfill migration', 'ACTIVE')");
      statement.execute(
          "INSERT INTO production.prod_product "
              + "(id, tenant_id, uid, product_type, unit, is_active) VALUES ('"
              + productId
              + "', '"
              + tenantId
              + "', 'YBF-MIG-PROD-"
              + productId
              + "', 'YARN', 'KG', TRUE)");
      statement.execute(
          "INSERT INTO production.prod_yarn_article "
              + "(id, tenant_id, uid, product_id, status, name, is_active) VALUES ('"
              + articleId
              + "', '"
              + tenantId
              + "', 'YBF-MIG-ARTICLE-"
              + articleId
              + "', '"
              + productId
              + "', 'DRAFT', 'Migration draft', TRUE)");
      statement.execute(queueInsert(tenantId, productId, articleId, "OPEN"));
      assertThatThrownBy(
              () -> statement.execute(queueInsert(tenantId, productId, articleId, "OPEN")))
          .isInstanceOf(SQLException.class);
      statement.execute(queueInsert(tenantId, productId, articleId, "RESOLVED"));
    }
  }

  private static String queueInsert(UUID tenantId, UUID productId, UUID articleId, String status) {
    return "INSERT INTO production.prod_yarn_backfill_reconciliation "
        + "(tenant_id, uid, product_id, article_id, reason, status, candidates) VALUES ('"
        + tenantId
        + "', 'YBF-MIG-QUEUE-"
        + UUID.randomUUID()
        + "', '"
        + productId
        + "', '"
        + articleId
        + "', 'AMBIGUOUS', '"
        + status
        + "', '{\"schemaVersion\":1,\"candidates\":[]}'::jsonb)";
  }

  private static String constraint(String name) throws SQLException {
    return string(
        "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname='" + name + "'");
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
