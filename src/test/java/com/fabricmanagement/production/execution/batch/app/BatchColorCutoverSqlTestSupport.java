package com.fabricmanagement.production.execution.batch.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.springframework.core.io.ClassPathResource;
import org.testcontainers.containers.PostgreSQLContainer;

abstract class BatchColorCutoverSqlTestSupport {

  static final String CUTOVER_SCRIPT = "db/migration/V20260718120000__batch_color_cutover.sql";
  static final String ROLLBACK_SCRIPT =
      "db/rollback/V20260718120000_ROLLBACK__batch_color_cutover.sql";
  static final UUID TENANT_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  static final UUID TENANT_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  static final UUID PRODUCT_A = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001");
  static final UUID PRODUCT_B = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000001");
  static final UUID NAVY_A = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000011");
  static final UUID ECRU_A = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000012");
  static final UUID OLD_A = UUID.fromString("aaaaaaaa-0000-4000-8000-000000000013");
  static final UUID NAVY_B = UUID.fromString("bbbbbbbb-0000-4000-8000-000000000011");

  protected final PostgreSQLContainer<?> postgres;

  BatchColorCutoverSqlTestSupport(PostgreSQLContainer<?> postgres) {
    this.postgres = postgres;
  }

  void resetToPreCutover() throws SQLException {
    try (Connection connection = ownerConnection();
        Statement statement = connection.createStatement()) {
      List<String> schemas =
          List.of(
              "common_ai",
              "common_approval",
              "common_audit",
              "common_auth",
              "common_communication",
              "common_company",
              "common_infrastructure",
              "common_policy",
              "common_tenant",
              "common_user",
              "costing",
              "finance",
              "flowboard",
              "human",
              "i18n",
              "iwm",
              "logistics",
              "notification",
              "procurement",
              "production",
              "sales",
              "sales_ord");
      for (String schema : schemas) {
        statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
      }
      statement.execute("DROP SCHEMA public CASCADE");
      statement.execute("CREATE SCHEMA public");
    }

    Flyway.configure()
        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
        .locations("classpath:db/migration")
        .schemas("common_tenant")
        .defaultSchema("common_tenant")
        .target("20260717120000")
        .load()
        .migrate();
  }

  void seedFoundation() throws SQLException {
    execute(
        """
        INSERT INTO common_tenant.common_tenant (id, uid, slug, name, status)
        VALUES
          ('%s', 'TENANT-A', 'tenant-a', 'Tenant A', 'ACTIVE'),
          ('%s', 'TENANT-B', 'tenant-b', 'Tenant B', 'ACTIVE');

        INSERT INTO production.prod_product
          (id, tenant_id, uid, product_type, unit)
        VALUES
          ('%s', '%s', 'PRODUCT-A', 'FABRIC', 'MT'),
          ('%s', '%s', 'PRODUCT-B', 'FABRIC', 'MT');

        INSERT INTO production.color
          (id, uid, created_at, updated_at, tenant_id, is_active, version, code, name)
        VALUES
          ('%s', 'COLOR-NAVY-A', now(), now(), '%s', true, 0, 'NAVY-01', 'Navy'),
          ('%s', 'COLOR-ECRU-A', now(), now(), '%s', true, 0, 'ECRU-02', 'Ecru'),
          ('%s', 'COLOR-OLD-A', now(), now(), '%s', false, 0, 'OLD-03', 'Old blue'),
          ('%s', 'COLOR-NAVY-B', now(), now(), '%s', true, 0, 'NAVY-01', 'Navy');
        """
            .formatted(
                TENANT_A, TENANT_B, PRODUCT_A, TENANT_A, PRODUCT_B, TENANT_B, NAVY_A, TENANT_A,
                ECRU_A, TENANT_A, OLD_A, TENANT_A, NAVY_B, TENANT_B));
  }

  UUID insertAttribute(UUID tenantId, String code, String suffix) throws SQLException {
    UUID id = UUID.randomUUID();
    execute(
        """
        INSERT INTO production.prod_product_attribute
          (id, tenant_id, uid, attribute_code, attribute_name)
        VALUES ('%s', '%s', 'ATTR-%s', '%s', 'Colour');
        """
            .formatted(id, tenantId, suffix, code.replace("'", "''")));
    return id;
  }

  UUID insertBatch(UUID tenantId, UUID productId, String suffix) throws SQLException {
    UUID id = UUID.randomUUID();
    execute(
        """
        INSERT INTO production.production_execution_batch
          (id, tenant_id, uid, product_id, product_type, batch_code, quantity,
           reserved_quantity, consumed_quantity, waste_quantity, unit, status)
        VALUES
          ('%s', '%s', 'BATCH-%s', '%s', 'FABRIC', 'LOT-%s', 10, 0, 0, 0, 'MT', 'AVAILABLE');
        """
            .formatted(id, tenantId, suffix, productId, suffix));
    return id;
  }

  UUID insertBatchAttribute(
      UUID tenantId, UUID batchId, UUID attributeId, String value, String suffix)
      throws SQLException {
    UUID id = UUID.randomUUID();
    String sqlValue = value == null ? "NULL" : "'" + value.replace("'", "''") + "'";
    execute(
        """
        INSERT INTO production.production_execution_batch_attribute
          (id, tenant_id, uid, batch_id, attribute_id, value, is_active,
           created_at, created_by, updated_at, updated_by, version)
        VALUES
          ('%s', '%s', 'BA-%s', '%s', '%s', %s, true,
           '2026-07-01 10:00:00', NULL, '2026-07-02 11:00:00', NULL, 7);
        """
            .formatted(id, tenantId, suffix, batchId, attributeId, sqlValue));
    return id;
  }

  void applyScript(String path) throws SQLException {
    if (CUTOVER_SCRIPT.equals(path)) {
      Flyway.configure()
          .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
          .locations("classpath:db/migration")
          .schemas("common_tenant")
          .defaultSchema("common_tenant")
          .target("20260718120000")
          .load()
          .migrate();
      return;
    }

    try (Connection connection = ownerConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(readClasspathScript(path));
    }
  }

  private String readClasspathScript(String path) {
    try (var input = new ClassPathResource(path).getInputStream()) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException("Could not read SQL script: " + path, exception);
    }
  }

  void execute(String sql) throws SQLException {
    try (Connection connection = ownerConnection();
        Statement statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }

  Connection ownerConnection() throws SQLException {
    return DriverManager.getConnection(
        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }

  boolean columnExists(String tableName, String columnName) throws SQLException {
    try (Connection connection = ownerConnection();
        var statement =
            connection.prepareStatement(
                """
                SELECT EXISTS (
                  SELECT 1 FROM information_schema.columns
                  WHERE table_schema = 'production' AND table_name = ? AND column_name = ?
                )
                """)) {
      statement.setString(1, tableName);
      statement.setString(2, columnName);
      try (var result = statement.executeQuery()) {
        result.next();
        return result.getBoolean(1);
      }
    }
  }

  boolean tableExists(String tableName) throws SQLException {
    try (Connection connection = ownerConnection();
        var statement =
            connection.prepareStatement("SELECT to_regclass('production.' || ?) IS NOT NULL")) {
      statement.setString(1, tableName);
      try (var result = statement.executeQuery()) {
        result.next();
        return result.getBoolean(1);
      }
    }
  }

  long count(String sql) throws SQLException {
    try (Connection connection = ownerConnection();
        Statement statement = connection.createStatement();
        var result = statement.executeQuery(sql)) {
      result.next();
      return result.getLong(1);
    }
  }
}
