package com.fabricmanagement.product.yarn.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
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
  private static final UUID PREEXISTING_TENANT = UUID.randomUUID();
  private static final UUID PREEXISTING_PRODUCT = UUID.randomUUID();
  private static final UUID PREEXISTING_ARTICLE = UUID.randomUUID();
  private static final UUID PREEXISTING_QUEUE = UUID.randomUUID();
  private static final UUID PREEXISTING_BATCH = UUID.randomUUID();
  private static final UUID PREEXISTING_TRANSACTION = UUID.randomUUID();

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.2-alpine"))
          .withDatabaseName("yarn_backfill_queue_migration")
          .withUsername("fabric_owner")
          .withPassword("fabric123");

  @BeforeAll
  static void migrate() throws SQLException {
    Flyway.configure()
        .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .schemas("common_tenant")
        .defaultSchema("common_tenant")
        .target(MIGRATION_VERSION)
        .load()
        .migrate();
    insertPre1eData();
    Flyway.configure()
        .configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .schemas("common_tenant")
        .defaultSchema("common_tenant")
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

  @Test
  void oneDDataSurvivesWithGeneratedCountAndBlankRemainsForTheRunner() throws SQLException {
    assertThat(
            count(
                "SELECT count(*) FROM production.prod_yarn_backfill_reconciliation "
                    + "WHERE id='"
                    + PREEXISTING_QUEUE
                    + "' AND status='OPEN' AND resolution_action IS NULL "
                    + "AND resolved_candidate IS NULL AND candidate_occurrence_count=2"))
        .isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM production.prod_yarn_article WHERE id='"
                    + PREEXISTING_ARTICLE
                    + "' AND source_designation=' '"))
        .isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM production.production_execution_inventory_transaction "
                    + "WHERE id='"
                    + PREEXISTING_TRANSACTION
                    + "' AND transaction_type='RECEIPT'"))
        .isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM pg_constraint "
                    + "WHERE conname='ck_inv_txn_type_valid' "
                    + "AND conrelid='production.production_execution_inventory_transaction'::regclass "
                    + "AND convalidated"))
        .isEqualTo(1);
  }

  @Test
  void resolutionConsistencyChecksRejectImpossibleRows() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute("SET app.current_tenant = '" + PREEXISTING_TENANT + "'");
      assertThatThrownBy(
              () ->
                  statement.execute(
                      "UPDATE production.prod_yarn_backfill_reconciliation "
                          + "SET resolution_action='DISMISSED' WHERE id='"
                          + PREEXISTING_QUEUE
                          + "'"))
          .isInstanceOf(SQLException.class);
      assertThatThrownBy(
              () ->
                  statement.execute(
                      "UPDATE production.prod_yarn_backfill_reconciliation "
                          + "SET status='RESOLVED' WHERE id='"
                          + PREEXISTING_QUEUE
                          + "'"))
          .isInstanceOf(SQLException.class);
    }
  }

  private static String queueInsert(UUID tenantId, UUID productId, UUID articleId, String status) {
    return "INSERT INTO production.prod_yarn_backfill_reconciliation "
        + "(tenant_id, uid, product_id, article_id, reason, status, candidates, resolution_action) VALUES ('"
        + tenantId
        + "', 'YBF-MIG-QUEUE-"
        + UUID.randomUUID()
        + "', '"
        + productId
        + "', '"
        + articleId
        + "', 'AMBIGUOUS', '"
        + status
        + "', '{\"schemaVersion\":1,\"candidates\":[]}'::jsonb, "
        + ("RESOLVED".equals(status) ? "'DISMISSED'" : "NULL")
        + ")";
  }

  private static void insertPre1eData() throws SQLException {
    try (Connection connection = connection();
        Statement statement = connection.createStatement()) {
      statement.execute("SET app.current_tenant = '" + PREEXISTING_TENANT + "'");
      statement.execute(
          "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, status) VALUES ('"
              + PREEXISTING_TENANT
              + "', 'YBF-PRE-1E', 'ybf-pre-1e-"
              + PREEXISTING_TENANT
              + "', 'Yarn pre 1E', 'ACTIVE')");
      statement.execute(
          "INSERT INTO production.prod_product "
              + "(id, tenant_id, uid, product_type, unit, is_active) VALUES ('"
              + PREEXISTING_PRODUCT
              + "', '"
              + PREEXISTING_TENANT
              + "', 'YBF-PRE-PRODUCT-"
              + PREEXISTING_PRODUCT
              + "', 'YARN', 'KG', TRUE)");
      statement.execute(
          "INSERT INTO production.prod_yarn_article "
              + "(id, tenant_id, uid, product_id, status, name, source_designation, is_active) "
              + "VALUES ('"
              + PREEXISTING_ARTICLE
              + "', '"
              + PREEXISTING_TENANT
              + "', 'YBF-PRE-ARTICLE-"
              + PREEXISTING_ARTICLE
              + "', '"
              + PREEXISTING_PRODUCT
              + "', 'DRAFT', 'Pre 1E draft', ' ', TRUE)");
      statement.execute(
          "INSERT INTO production.prod_yarn_backfill_reconciliation "
              + "(id, tenant_id, uid, product_id, article_id, reason, status, candidates) VALUES ('"
              + PREEXISTING_QUEUE
              + "', '"
              + PREEXISTING_TENANT
              + "', 'YBF-PRE-QUEUE-"
              + PREEXISTING_QUEUE
              + "', '"
              + PREEXISTING_PRODUCT
              + "', '"
              + PREEXISTING_ARTICLE
              + "', 'AMBIGUOUS', 'OPEN', "
              + "'{\"schemaVersion\":1,\"candidates\":[{\"rawValue\":\"A\"},{\"rawValue\":\"B\"}]}'::jsonb)");
      statement.execute(
          "INSERT INTO production.production_execution_batch "
              + "(id, tenant_id, uid, product_id, product_type, batch_code, quantity, "
              + "reserved_quantity, consumed_quantity, waste_quantity, unit, status, is_active) "
              + "VALUES ('"
              + PREEXISTING_BATCH
              + "', '"
              + PREEXISTING_TENANT
              + "', 'YBF-PRE-BATCH-"
              + PREEXISTING_BATCH
              + "', '"
              + PREEXISTING_PRODUCT
              + "', 'YARN', 'YBF-PRE-BATCH-CODE-"
              + PREEXISTING_BATCH
              + "', 1, 0, 0, 0, 'KG', 'AVAILABLE', TRUE)");
      statement.execute(
          "INSERT INTO production.production_execution_inventory_transaction "
              + "(id, tenant_id, uid, batch_id, transaction_type, quantity, unit, "
              + "transaction_date, is_active) VALUES ('"
              + PREEXISTING_TRANSACTION
              + "', '"
              + PREEXISTING_TENANT
              + "', 'YBF-PRE-TXN-"
              + PREEXISTING_TRANSACTION
              + "', '"
              + PREEXISTING_BATCH
              + "', 'RECEIPT', 1, 'KG', NOW(), TRUE)");
    }
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
