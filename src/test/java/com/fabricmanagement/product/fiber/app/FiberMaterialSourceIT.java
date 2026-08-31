package com.fabricmanagement.product.fiber.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@TestMethodOrder(OrderAnnotation.class)
class FiberMaterialSourceIT {

  private static final String PREVIOUS_VERSION = "20260826110000";
  private static final String MIGRATION_VERSION = "20260828120000";

  @Container static final PostgreSQLContainer<?> POSTGRES = newPostgres("fiber_material_source");

  @BeforeAll
  static void migrateToPreviousVersion() {
    migrateTo(POSTGRES, PREVIOUS_VERSION);
  }

  @Test
  @Order(1)
  void migrationAddsNullableColumnsChecksAndExactPartialIndexes() throws SQLException {
    migrateTo(POSTGRES, MIGRATION_VERSION);

    assertThat(nullableColumn(POSTGRES, "prod_fiber", "material_source")).isTrue();
    assertThat(nullableColumn(POSTGRES, "production_fiber_request", "material_source")).isTrue();
    assertThat(count(POSTGRES, "SELECT count(material_source) FROM production.prod_fiber"))
        .isZero();
    assertThat(
            count(
                POSTGRES, "SELECT count(material_source) FROM production.production_fiber_request"))
        .isZero();

    assertThat(constraintDefinition(POSTGRES, "chk_fiber_material_source"))
        .contains("VIRGIN", "RECYCLED");
    assertThat(constraintDefinition(POSTGRES, "chk_fiber_material_source_pure_only"))
        .contains("COALESCE(composition, '{}'::jsonb) = '{}'::jsonb");
    assertThat(constraintDefinition(POSTGRES, "chk_fiber_request_material_source"))
        .contains("VIRGIN", "RECYCLED");
    assertThat(indexDefinition(POSTGRES, "uq_fiber_request_open_iso_source").toLowerCase())
        .contains("upper((iso_code)::text)")
        .contains("coalesce(material_source, 'undeclared'::character varying)")
        .contains("status", "pending", "approved");
    assertThat(indexDefinition(POSTGRES, "uq_fiber_active_pure_iso_source").toLowerCase())
        .contains("fiber_iso_code_id")
        .contains("coalesce(material_source, 'undeclared'::character varying)")
        .contains("is_active = true")
        .contains("coalesce(composition, '{}'::jsonb) = '{}'::jsonb");
  }

  @Test
  @Order(2)
  void databaseRejectsInvalidLiteralAndSourceOnBlend() throws SQLException {
    assertThatThrownBy(
            () ->
                execute(
                    POSTGRES,
                    "UPDATE production.prod_fiber SET material_source = 'USED' "
                        + "WHERE id = (SELECT id FROM production.prod_fiber LIMIT 1)"))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("chk_fiber_material_source");

    String blendId = UUID.randomUUID().toString();
    insertFiberFixture(POSTGRES, UUID.randomUUID(), blendId, "{\"component\":100}");
    assertThatThrownBy(
            () ->
                execute(
                    POSTGRES,
                    "UPDATE production.prod_fiber SET material_source = 'RECYCLED' "
                        + "WHERE id = '"
                        + blendId
                        + "'::uuid"))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("chk_fiber_material_source_pure_only");
  }

  @Test
  void preflightFailsOnDuplicateActivePureVariants() throws SQLException {
    try (PostgreSQLContainer<?> database = newPostgres("fiber_source_preflight")) {
      database.start();
      migrateTo(database, PREVIOUS_VERSION);
      UUID tenantId = UUID.randomUUID();
      UUID isoId = UUID.randomUUID();
      String first = UUID.randomUUID().toString();
      String second = UUID.randomUUID().toString();
      insertTenantAndReferences(database, tenantId, isoId);
      insertFiberFixture(database, tenantId, isoId, first, "{}");
      insertFiberFixture(database, tenantId, isoId, second, "{}");

      assertThatThrownBy(() -> migrateTo(database, MIGRATION_VERSION))
          .isInstanceOf(FlywayException.class)
          .hasStackTraceContaining("duplicate active pure fiber variants");
    }
  }

  @Test
  void preflightAndIndexAllowTwoBlendsSharingPrimaryIso() throws SQLException {
    try (PostgreSQLContainer<?> database = newPostgres("fiber_source_blends")) {
      database.start();
      migrateTo(database, PREVIOUS_VERSION);
      UUID tenantId = UUID.randomUUID();
      UUID isoId = UUID.randomUUID();
      insertTenantAndReferences(database, tenantId, isoId);
      insertFiberFixture(
          database, tenantId, isoId, UUID.randomUUID().toString(), "{\"pes\":60,\"co\":40}");
      insertFiberFixture(
          database, tenantId, isoId, UUID.randomUUID().toString(), "{\"pes\":70,\"cv\":30}");

      migrateTo(database, MIGRATION_VERSION);

      assertThat(
              count(
                  database,
                  "SELECT count(*) FROM production.prod_fiber WHERE tenant_id = '"
                      + tenantId
                      + "'::uuid"))
          .isEqualTo(2L);
    }
  }

  private static void insertFiberFixture(
      PostgreSQLContainer<?> database, UUID tenantId, String fiberId, String composition)
      throws SQLException {
    UUID isoId = UUID.randomUUID();
    insertTenantAndReferences(database, tenantId, isoId);
    insertFiberFixture(database, tenantId, isoId, fiberId, composition);
  }

  private static void insertTenantAndReferences(
      PostgreSQLContainer<?> database, UUID tenantId, UUID isoId) throws SQLException {
    String tenantSuffix = tenantId.toString().substring(0, 8);
    execute(
        database,
        "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, status) VALUES ('"
            + tenantId
            + "', 'MIG-"
            + tenantSuffix
            + "', 'mig-"
            + tenantSuffix
            + "', 'Migration Tenant', 'ACTIVE') ON CONFLICT (id) DO NOTHING");
    execute(
        database,
        "INSERT INTO production.prod_fiber_category "
            + "(id, tenant_id, uid, category_code, category_name) VALUES ('"
            + UUID.nameUUIDFromBytes((tenantId + "category").getBytes())
            + "', '"
            + tenantId
            + "', 'MIG-FCAT-"
            + tenantSuffix
            + "', 'MIG_CATEGORY_"
            + tenantSuffix
            + "', 'Migration Category') ON CONFLICT DO NOTHING");
    execute(
        database,
        "INSERT INTO production.prod_fiber_iso_code "
            + "(id, tenant_id, uid, iso_code, fiber_name, fiber_type, is_official_iso) VALUES ('"
            + isoId
            + "', '"
            + tenantId
            + "', 'MIG-FISO-"
            + isoId.toString().substring(0, 8)
            + "', 'M"
            + isoId.toString().substring(0, 7)
            + "', 'Migration Fiber', 'MIG_CATEGORY_"
            + tenantSuffix
            + "', FALSE) ON CONFLICT (id) DO NOTHING");
  }

  private static void insertFiberFixture(
      PostgreSQLContainer<?> database,
      UUID tenantId,
      UUID isoId,
      String fiberId,
      String composition)
      throws SQLException {
    UUID productId = UUID.nameUUIDFromBytes((fiberId + "product").getBytes());
    UUID categoryId = UUID.nameUUIDFromBytes((tenantId + "category").getBytes());
    execute(
        database,
        "INSERT INTO production.prod_product "
            + "(id, tenant_id, uid, product_type, unit) VALUES ('"
            + productId
            + "', '"
            + tenantId
            + "', 'MIG-PROD-"
            + fiberId.substring(0, 8)
            + "', 'FIBER', 'KG')");
    execute(
        database,
        "INSERT INTO production.prod_fiber "
            + "(id, tenant_id, uid, product_id, fiber_category_id, fiber_iso_code_id, "
            + "fiber_name, composition, status) VALUES ('"
            + fiberId
            + "', '"
            + tenantId
            + "', 'MIG-FIBR-"
            + fiberId.substring(0, 8)
            + "', '"
            + productId
            + "', '"
            + categoryId
            + "', '"
            + isoId
            + "', 'Migration Fiber', '"
            + composition
            + "'::jsonb, 'ACTIVE')");
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

  private static boolean nullableColumn(
      PostgreSQLContainer<?> database, String table, String column) throws SQLException {
    return "YES"
        .equals(
            string(
                database,
                "SELECT is_nullable FROM information_schema.columns "
                    + "WHERE table_schema = 'production' AND table_name = '"
                    + table
                    + "' AND column_name = '"
                    + column
                    + "'"));
  }

  private static String constraintDefinition(PostgreSQLContainer<?> database, String constraintName)
      throws SQLException {
    return string(
        database,
        "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = '"
            + constraintName
            + "'");
  }

  private static String indexDefinition(PostgreSQLContainer<?> database, String indexName)
      throws SQLException {
    return string(
        database,
        "SELECT indexdef FROM pg_indexes WHERE schemaname = 'production' AND indexname = '"
            + indexName
            + "'");
  }

  private static long count(PostgreSQLContainer<?> database, String sql) throws SQLException {
    try (Connection connection = connection(database);
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      result.next();
      return result.getLong(1);
    }
  }

  private static String string(PostgreSQLContainer<?> database, String sql) throws SQLException {
    try (Connection connection = connection(database);
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      result.next();
      return result.getString(1);
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
