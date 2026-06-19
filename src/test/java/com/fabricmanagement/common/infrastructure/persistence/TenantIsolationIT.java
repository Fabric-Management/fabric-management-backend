package com.fabricmanagement.common.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * T5: Two-tenant isolation proof using real RLS.
 *
 * <p>Creates a {@code fabric_app} role (NOBYPASSRLS) inside the Testcontainers Postgres and
 * verifies that RLS policies enforce tenant isolation at the database level.
 *
 * <p><b>CRITICAL:</b> The default Testcontainers user is a superuser which bypasses RLS. All
 * isolation tests here use a separate JDBC connection as {@code fabric_app}.
 *
 * <h2>Test Cases:</h2>
 *
 * <ol>
 *   <li>Cross-tenant READ isolation (Tenant A context → only A data)
 *   <li>Cross-tenant READ isolation (Tenant B context → only B data)
 *   <li>WITH CHECK enforcement (Tenant A context → INSERT with Tenant B ID → error)
 *   <li>Tenant table self-row (Tenant A context → tenant table → only A row)
 *   <li>Empty context (no current_tenant) → 0 rows
 *   <li>Owner bypass (superuser) → all tenant data visible
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TenantIsolationIT {

  private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  private static final String STRICT_PARTNER_UID = "TEST-A-STRICT-TP";
  private static final String STRICT_REGISTRY_UID = "TEST-A-STRICT-REG";

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
          .withDatabaseName("fabric_test")
          .withUsername("fabric_owner")
          .withPassword("fabric123");

  @DynamicPropertySource
  static void registerPgProperties(DynamicPropertyRegistry registry) {
    // 1. Create fabric_app role BEFORE Flyway runs, so Flyway can grant privileges to it
    try (Connection conn =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      Statement stmt = conn.createStatement();
      stmt.execute(
          "DO $$ BEGIN "
              + "IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'fabric_app') THEN "
              + "CREATE ROLE fabric_app LOGIN NOSUPERUSER NOCREATEDB NOBYPASSRLS PASSWORD 'app_test'; "
              + "END IF; END $$");
    } catch (Exception e) {
      throw new RuntimeException("Failed to create fabric_app role", e);
    }

    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
    registry.add("spring.flyway.enabled", () -> "true");
  }

  @Autowired private JdbcTemplate ownerJdbc;

  private static boolean initialized = false;

  @org.junit.jupiter.api.BeforeEach
  void setUp() throws Exception {
    if (!initialized) {
      setupSeedData();
      initialized = true;
    }
  }

  private void setupSeedData() throws Exception {
    try (Connection conn =
        DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      Statement stmt = conn.createStatement();

      // 3. Seed two test tenants in tenant table (via owner — bypasses RLS)
      stmt.execute(
          "INSERT INTO common_tenant.common_tenant "
              + "(id, uid, slug, name, status, settings, is_active, created_at, updated_at, version) "
              + "VALUES ('"
              + TENANT_A
              + "', 'TEST-A', 'test-a', 'Test Tenant A', 'ACTIVE', "
              + "'{}'::jsonb, true, now(), now(), 0) "
              + "ON CONFLICT (id) DO NOTHING");
      stmt.execute(
          "INSERT INTO common_tenant.common_tenant "
              + "(id, uid, slug, name, status, settings, is_active, created_at, updated_at, version) "
              + "VALUES ('"
              + TENANT_B
              + "', 'TEST-B', 'test-b', 'Test Tenant B', 'ACTIVE', "
              + "'{}'::jsonb, true, now(), now(), 0) "
              + "ON CONFLICT (id) DO NOTHING");

      // 4. Seed test data in a tenant-scoped table (fiber category)
      // uid constraint is now tenant-scoped: UNIQUE(tenant_id, uid).
      // Use NOT EXISTS guard for idempotency since ON CONFLICT (uid) alone won't work.
      stmt.execute(
          "INSERT INTO production.prod_fiber_category "
              + "(id, tenant_id, uid, category_code, category_name, display_order, is_active, created_at, updated_at, version) "
              + "SELECT gen_random_uuid(), '"
              + TENANT_A
              + "', 'TEST-A-CAT', 'TEST_A_CAT', 'Tenant A Category', 1, true, now(), now(), 0 "
              + "WHERE NOT EXISTS (SELECT 1 FROM production.prod_fiber_category WHERE tenant_id = '"
              + TENANT_A
              + "' AND uid = 'TEST-A-CAT')");
      stmt.execute(
          "INSERT INTO production.prod_fiber_category "
              + "(id, tenant_id, uid, category_code, category_name, display_order, is_active, created_at, updated_at, version) "
              + "SELECT gen_random_uuid(), '"
              + TENANT_B
              + "', 'TEST-B-CAT', 'TEST_B_CAT', 'Tenant B Category', 1, true, now(), now(), 0 "
              + "WHERE NOT EXISTS (SELECT 1 FROM production.prod_fiber_category WHERE tenant_id = '"
              + TENANT_B
              + "' AND uid = 'TEST-B-CAT')");

      stmt.execute(
          "INSERT INTO common_company.trading_partner_registry "
              + "(uid, official_name, country, verified_status, is_active, created_at, updated_at, version) "
              + "VALUES ('"
              + STRICT_REGISTRY_UID
              + "', 'Strict Isolation Partner Registry', 'TUR', 'UNVERIFIED', true, now(), now(), 0) "
              + "ON CONFLICT (uid) DO NOTHING");
      stmt.execute(
          "INSERT INTO common_company.common_trading_partner "
              + "(tenant_id, uid, registry_id, custom_name, partner_type, status, relationship_meta, "
              + "is_active, created_at, updated_at, version) "
              + "SELECT '"
              + TENANT_A
              + "', '"
              + STRICT_PARTNER_UID
              + "', r.id, 'Strict Isolation Partner', 'CUSTOMER', 'ACTIVE', '{}'::jsonb, "
              + "true, now(), now(), 0 "
              + "FROM common_company.trading_partner_registry r "
              + "WHERE r.uid = '"
              + STRICT_REGISTRY_UID
              + "' "
              + "AND NOT EXISTS (SELECT 1 FROM common_company.common_trading_partner WHERE uid = '"
              + STRICT_PARTNER_UID
              + "')");

      stmt.close();
    }
  }

  /** Get a JDBC connection as fabric_app (NOBYPASSRLS) with the given tenant context set. */
  private Connection getAppConnection(UUID tenantId) throws Exception {
    Connection conn = DriverManager.getConnection(postgres.getJdbcUrl(), "fabric_app", "app_test");
    conn.setAutoCommit(true);
    if (tenantId != null) {
      try (Statement stmt = conn.createStatement()) {
        stmt.execute("SET app.current_tenant = '" + tenantId + "'");
      }
    }
    return conn;
  }

  @Test
  @Order(1)
  @DisplayName("T5-1: Tenant A context → sees only Tenant A data")
  void tenantA_seesOnlyOwnData() throws Exception {
    try (Connection conn = getAppConnection(TENANT_A)) {
      PreparedStatement ps =
          conn.prepareStatement("SELECT category_code FROM production.prod_fiber_category");
      ResultSet rs = ps.executeQuery();

      List<String> codes = new ArrayList<>();
      while (rs.next()) {
        codes.add(rs.getString(1));
      }

      assertThat(codes).contains("TEST_A_CAT").doesNotContain("TEST_B_CAT");
    }
  }

  @Test
  @Order(2)
  @DisplayName("T5-2: Tenant B context → sees only Tenant B data")
  void tenantB_seesOnlyOwnData() throws Exception {
    try (Connection conn = getAppConnection(TENANT_B)) {
      PreparedStatement ps =
          conn.prepareStatement("SELECT category_code FROM production.prod_fiber_category");
      ResultSet rs = ps.executeQuery();

      List<String> codes = new ArrayList<>();
      while (rs.next()) {
        codes.add(rs.getString(1));
      }

      assertThat(codes).contains("TEST_B_CAT").doesNotContain("TEST_A_CAT");
    }
  }

  @Test
  @Order(3)
  @DisplayName("T5-3: WITH CHECK — Tenant A context → INSERT with Tenant B ID → error")
  void withCheck_preventsInsertToOtherTenant() throws Exception {
    try (Connection conn = getAppConnection(TENANT_A)) {
      assertThatThrownBy(
              () -> {
                PreparedStatement ps =
                    conn.prepareStatement(
                        "INSERT INTO production.prod_fiber_category "
                            + "(id, tenant_id, uid, category_code, category_name, display_order, "
                            + "is_active, created_at, updated_at, version) "
                            + "VALUES (gen_random_uuid(), ?, 'HACK-CAT', 'HACK', 'Hacked', 1, "
                            + "true, now(), now(), 0)");
                ps.setObject(1, TENANT_B);
                ps.execute();
              })
          .hasMessageContaining("row-level security");
    }
  }

  @Test
  @Order(4)
  @DisplayName("T5-4: Tenant table self-row — Tenant A sees only own row")
  void tenantTable_selfRowIsolation() throws Exception {
    try (Connection conn = getAppConnection(TENANT_A)) {
      PreparedStatement ps =
          conn.prepareStatement("SELECT id, name FROM common_tenant.common_tenant");
      ResultSet rs = ps.executeQuery();

      List<UUID> ids = new ArrayList<>();
      while (rs.next()) {
        ids.add(UUID.fromString(rs.getString("id")));
      }

      assertThat(ids).containsExactly(TENANT_A).doesNotContain(TENANT_B);
    }
  }

  @Test
  @Order(5)
  @DisplayName("T5-5: Empty context (no current_tenant set) → 0 rows (deny-by-default)")
  void emptyContext_returnsZeroRows() throws Exception {
    Integer ownerCount =
        ownerJdbc.queryForObject(
            "SELECT count(*) FROM common_company.common_trading_partner WHERE uid = ?",
            Integer.class,
            STRICT_PARTNER_UID);
    assertThat(ownerCount).isEqualTo(1);

    try (Connection conn = getAppConnection(TENANT_A)) {
      PreparedStatement ps =
          conn.prepareStatement(
              "SELECT count(*) FROM common_company.common_trading_partner WHERE uid = ?");
      ps.setString(1, STRICT_PARTNER_UID);
      ResultSet rs = ps.executeQuery();
      rs.next();

      assertThat(rs.getInt(1)).isOne();
    }

    try (Connection conn = getAppConnection(TENANT_B)) {
      PreparedStatement ps =
          conn.prepareStatement(
              "SELECT count(*) FROM common_company.common_trading_partner WHERE uid = ?");
      ps.setString(1, STRICT_PARTNER_UID);
      ResultSet rs = ps.executeQuery();
      rs.next();

      assertThat(rs.getInt(1)).isZero();
    }

    try (Connection conn = getAppConnection(null)) {
      PreparedStatement ps =
          conn.prepareStatement(
              "SELECT count(*) FROM common_company.common_trading_partner WHERE uid = ?");
      ps.setString(1, STRICT_PARTNER_UID);
      ResultSet rs = ps.executeQuery();
      rs.next();

      assertThat(rs.getInt(1)).isZero();
    }
  }

  @Test
  @Order(6)
  @DisplayName("T5-6: Owner bypass — superuser sees all tenant data")
  void ownerBypass_seesAllData() {
    // ownerJdbc uses the Testcontainers default user (superuser → bypasses RLS)
    Integer count =
        ownerJdbc.queryForObject(
            "SELECT count(DISTINCT tenant_id) FROM production.prod_fiber_category "
                + "WHERE tenant_id IN (?, ?)",
            Integer.class,
            TENANT_A,
            TENANT_B);

    assertThat(count).isEqualTo(2);
  }
}
