package com.fabricmanagement.product.fiber.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tenant.app.TenantClonerService;
import com.fabricmanagement.product.fiber.app.FiberService;
import com.fabricmanagement.product.fiber.domain.FiberCatalog;
import com.fabricmanagement.product.fiber.domain.FiberCatalogScope;
import com.fabricmanagement.product.fiber.dto.FiberDto;
import com.fabricmanagement.testsupport.PostgresImage;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * FIBER-CATALOG-1 shared catalogue on a fresh database, with the real runtime roles: {@code
 * fabric_app} (NOSUPERUSER, NOBYPASSRLS, not the owner) is the application datasource, {@code
 * fabric_system} the BYPASSRLS system datasource. Scenarios A01, A02, A03 (reference provisioning
 * copies nothing), A04 (normal-role SQL cannot change shared rows).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FiberSharedCatalogIT {

  private static final UUID OWNER = FiberCatalog.OWNER_ID;

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      PostgresImage.container()
          .withDatabaseName("fabric_test")
          .withUsername("fabric_owner")
          .withPassword("fabric123");

  @DynamicPropertySource
  static void configureDatasource(DynamicPropertyRegistry registry) {
    try (Connection conn =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Statement stmt = conn.createStatement()) {
      stmt.execute(
          "CREATE ROLE fabric_app LOGIN NOSUPERUSER NOCREATEDB NOBYPASSRLS PASSWORD 'app_test'");
      stmt.execute(
          "CREATE ROLE fabric_system LOGIN NOSUPERUSER NOCREATEDB BYPASSRLS PASSWORD 'system_test'");
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create test database roles", e);
    }
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", () -> "fabric_app");
    registry.add("spring.datasource.password", () -> "app_test");
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
    registry.add("application.system-datasource.username", () -> "fabric_system");
    registry.add("application.system-datasource.password", () -> "system_test");
  }

  @Autowired private FiberService fiberService;
  @Autowired private TenantClonerService tenantClonerService;
  @Autowired private PlatformTransactionManager transactionManager;

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  @Order(1)
  void a01_freshChainPublishesExactlyTheInitialSharedCatalogueAndNoTenantCopies() {
    JdbcTemplate owner = owner();
    assertThat(count(owner, "SELECT count(*) FROM production.prod_fiber_category")).isEqualTo(8);
    assertThat(count(owner, "SELECT count(*) FROM production.prod_fiber_certification"))
        .isEqualTo(12);
    assertThat(count(owner, "SELECT count(*) FROM production.prod_fiber_iso_code")).isEqualTo(52);
    assertThat(count(owner, "SELECT count(*) FROM production.prod_fiber")).isEqualTo(52);
    assertThat(
            count(
                owner,
                "SELECT count(*) FROM production.prod_product p JOIN production.prod_fiber f"
                    + " ON f.product_id = p.id"))
        .isEqualTo(52);
    for (String table :
        List.of(
            "prod_fiber_category",
            "prod_fiber_certification",
            "prod_fiber_iso_code",
            "prod_fiber",
            "prod_product")) {
      assertThat(
              count(
                  owner,
                  "SELECT count(*) FROM production." + table + " WHERE tenant_id <> ?",
                  OWNER))
          .as("%s holds catalogue-owner rows only", table)
          .isZero();
    }
    assertThat(
            count(
                owner,
                "SELECT count(*) FROM production.prod_fiber"
                    + " WHERE material_source IS NOT NULL OR composition <> '{}'::jsonb"))
        .as("canonical shared fibres are pure and undeclared")
        .isZero();
    assertThat(
            owner.queryForObject(
                "SELECT f.id FROM production.prod_fiber f JOIN production.prod_fiber_iso_code i"
                    + " ON i.id = f.fiber_iso_code_id WHERE i.iso_code = 'CO'",
                UUID.class))
        .as("identity comes from the checked-in seed definition")
        .isEqualTo(UUID.fromString("0f1bf1b0-0000-4000-8000-000000000001"));
    assertThat(
            owner.queryForObject(
                "SELECT p.uid FROM production.prod_product p WHERE p.id = ?",
                String.class,
                UUID.fromString("0f1bd000-0000-4000-8000-000000000036")))
        .isEqualTo("SYS-MAT-000036");
  }

  /** Runs last: it publishes an additional test entry into this class's own database. */
  @Test
  @Order(4)
  void a02_replayReorderAndOneNewEntryKeepEveryExistingMappingAndAMissingCategoryFailsAtomically()
      throws Exception {
    JdbcTemplate owner = owner();
    Map<UUID, UUID> before = fiberToIso(owner);
    String seed =
        new String(
            new ClassPathResource("db/migration/R__001__fiber_seeds.sql")
                .getInputStream()
                .readAllBytes(),
            StandardCharsets.UTF_8);

    owner.update(
        "UPDATE production.prod_fiber_iso_code SET display_order = 100 - display_order"
            + " WHERE tenant_id = ?",
        OWNER);
    List<Map<String, Object>> stamps =
        owner.queryForList("SELECT id, updated_at, version FROM production.prod_fiber ORDER BY id");
    owner.execute(seed);
    owner.execute(seed);

    assertThat(fiberToIso(owner)).isEqualTo(before);
    assertThat(
            owner.queryForList(
                "SELECT id, updated_at, version FROM production.prod_fiber ORDER BY id"))
        .as("a replay performs no no-op updates")
        .isEqualTo(stamps);

    UUID newIso = UUID.randomUUID();
    UUID newProduct = UUID.randomUUID();
    UUID newFiber = UUID.randomUUID();
    owner.update(
        "INSERT INTO production.prod_fiber_iso_code (id, tenant_id, uid, iso_code, fiber_name,"
            + " fiber_type, is_official_iso, display_order) VALUES (?, ?, 'FC1-TEST-ISO', 'TSTX',"
            + " 'Test Fibre', 'SYNTHETIC_POLYMER', FALSE, 53)",
        newIso,
        OWNER);
    owner.queryForList(
        "SELECT production.publish_fiber_catalog_entry('TSTX', ?, 'SYS-MAT-TEST01', ?,"
            + " 'SYS-FIB-TEST01', 'Test Fibre (100%)')",
        newProduct, newFiber);
    owner.execute(seed);

    Map<UUID, UUID> after = fiberToIso(owner);
    assertThat(after).containsAllEntriesOf(before).hasSize(before.size() + 1);
    assertThat(after).containsEntry(newFiber, newIso);

    owner.update(
        "INSERT INTO production.prod_fiber_iso_code (id, tenant_id, uid, iso_code, fiber_name,"
            + " fiber_type, is_official_iso) VALUES (gen_random_uuid(), ?, 'FC1-TEST-ISO2',"
            + " 'TSTW', 'Valid', 'SYNTHETIC_POLYMER', FALSE), (gen_random_uuid(), ?,"
            + " 'FC1-TEST-ISO3', 'TSTY', 'Orphan', 'NO_SUCH_CATEGORY', FALSE)",
        OWNER,
        OWNER);
    UUID partialProduct = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                owner.execute(
                    "DO $$ BEGIN PERFORM production.publish_fiber_catalog_entry('TSTZ', NULL,"
                        + " NULL, NULL, NULL, NULL); END $$"))
        .hasMessageContaining("every catalogue key is required");
    assertThatThrownBy(
            () ->
                owner.execute(
                    "DO $$ BEGIN PERFORM production.publish_fiber_catalog_entry('TSTW', '"
                        + partialProduct
                        + "', 'SYS-MAT-PARTIAL', gen_random_uuid(), 'SYS-FIB-PARTIAL',"
                        + " 'Valid (100%)');"
                        + " PERFORM production.publish_fiber_catalog_entry('TSTY',"
                        + " gen_random_uuid(), 'SYS-MAT-ORPHAN', gen_random_uuid(),"
                        + " 'SYS-FIB-ORPHAN', 'Orphan (100%)');"
                        + " END $$"))
        .hasMessageContaining("requires active category NO_SUCH_CATEGORY");
    assertThat(
            count(
                owner, "SELECT count(*) FROM production.prod_product WHERE id = ?", partialProduct))
        .as("a failed publication is atomic: the valid entry before it is rolled back too")
        .isZero();
    assertThat(fiberToIso(owner)).isEqualTo(after);
  }

  @Test
  @Order(2)
  void a03_referenceProvisioningCopiesNoFibreReferenceRowsAndTenantsReadTheSameSharedIds() {
    UUID regular = tenant("REGULAR");
    UUID playground = tenant("PLAYGROUND");

    tenantClonerService.cloneReferenceDataToTenant(regular);
    tenantClonerService.cloneReferenceDataToTenant(playground);

    for (String table :
        List.of("prod_fiber_category", "prod_fiber_certification", "prod_fiber_iso_code")) {
      assertThat(
              count(
                  owner(),
                  "SELECT count(*) FROM production." + table + " WHERE tenant_id IN (?, ?)",
                  regular,
                  playground))
          .isZero();
    }
    List<UUID> regularShared = sharedIdsAs(regular);
    List<UUID> playgroundShared = sharedIdsAs(playground);
    assertThat(regularShared).hasSize(52).isEqualTo(playgroundShared);
  }

  @Test
  @Order(3)
  void a04_theApplicationRoleCannotChangeSharedRowsButReadsThem() throws SQLException {
    UUID tenant = tenant("REGULAR");
    UUID sharedCotton = UUID.fromString("0f1bf1b0-0000-4000-8000-000000000001");
    UUID sharedCottonProduct = UUID.fromString("0f1bd000-0000-4000-8000-000000000001");
    UUID sharedCo = UUID.fromString("0f1b1500-0000-4000-8000-000000000001");

    try (Connection app =
        DriverManager.getConnection(postgres.getJdbcUrl(), "fabric_app", "app_test")) {
      try (Statement statement = app.createStatement()) {
        statement.execute("SET app.current_tenant = '" + tenant + "'");
        var visible =
            statement.executeQuery(
                "SELECT count(*) FROM production.prod_fiber WHERE id = '" + sharedCotton + "'");
        visible.next();
        assertThat(visible.getLong(1)).as("shared rows are readable").isEqualTo(1);

        assertThat(
                statement.executeUpdate(
                    "UPDATE production.prod_fiber SET fiber_name = 'Hijacked' WHERE id = '"
                        + sharedCotton
                        + "'"))
            .isZero();
        assertThat(
                statement.executeUpdate(
                    "UPDATE production.prod_product SET is_active = FALSE WHERE id = '"
                        + sharedCottonProduct
                        + "'"))
            .isZero();
        assertThat(
                statement.executeUpdate(
                    "DELETE FROM production.prod_fiber WHERE id = '" + sharedCotton + "'"))
            .isZero();
        assertThat(
                statement.executeUpdate(
                    "UPDATE production.prod_fiber_iso_code SET fiber_name = 'Hijacked' WHERE id"
                        + " = '"
                        + sharedCo
                        + "'"))
            .isZero();
      }
      assertThatThrownBy(
              () -> {
                try (Statement statement = app.createStatement()) {
                  statement.execute(
                      "INSERT INTO production.prod_fiber_iso_code (id, tenant_id, uid, iso_code,"
                          + " fiber_name, fiber_type, is_official_iso) VALUES (gen_random_uuid(),"
                          + " '"
                          + tenant
                          + "', 'TENANT-ISO', 'TNT', 'Tenant copy', 'NATURAL_PLANT', FALSE)");
                }
              })
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("chk_fiber_iso_code_catalog_owner");
      assertThatThrownBy(
              () -> {
                try (Statement statement = app.createStatement()) {
                  statement.execute(
                      "INSERT INTO production.prod_fiber_iso_code (id, tenant_id, uid, iso_code,"
                          + " fiber_name, fiber_type, is_official_iso) VALUES (gen_random_uuid(),"
                          + " '"
                          + OWNER
                          + "', 'FORGED-ISO', 'FRG', 'Forged', 'NATURAL_PLANT', FALSE)");
                }
              })
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("row-level security");
    }
    assertThat(
            owner()
                .queryForObject(
                    "SELECT fiber_name FROM production.prod_fiber WHERE id = ?",
                    String.class,
                    sharedCotton))
        .isEqualTo("Cotton (100%)");
  }

  private List<UUID> sharedIdsAs(UUID tenant) {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    Supplier<List<UUID>> read =
        () ->
            fiberService.getAll().stream()
                .filter(fiber -> fiber.getCatalogScope() == FiberCatalogScope.SHARED)
                .map(FiberDto::getId)
                .sorted()
                .toList();
    return TenantContext.executeInTenantContext(
        tenant,
        () -> {
          TenantContext.setCurrentTenantUid("FC1-" + tenant.toString().substring(0, 8));
          return transaction.execute(status -> read.get());
        });
  }

  private Map<UUID, UUID> fiberToIso(JdbcTemplate owner) {
    Map<UUID, UUID> result = new java.util.HashMap<>();
    owner.query(
        "SELECT id, fiber_iso_code_id FROM production.prod_fiber WHERE tenant_id = ?",
        rs -> {
          result.put((UUID) rs.getObject(1), (UUID) rs.getObject(2));
        },
        OWNER);
    return result;
  }

  private UUID tenant(String type) {
    UUID id = UUID.randomUUID();
    owner()
        .update(
            "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, type, billing_email,"
                + " status, settings, is_active, created_at, updated_at, version)"
                + " VALUES (?, ?, ?, ?, ?, 'fibre@example.com', 'ACTIVE', '{}', true, now(),"
                + " now(), 0)",
            id,
            "FC1-" + id.toString().substring(0, 8),
            "fibre-catalogue-" + id,
            "Fibre catalogue " + id,
            type);
    return id;
  }

  private static long count(JdbcTemplate jdbc, String sql, Object... args) {
    Long value = jdbc.queryForObject(sql, Long.class, args);
    return value == null ? 0 : value;
  }

  private JdbcTemplate owner() {
    return new JdbcTemplate(
        new DriverManagerDataSource(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
  }
}
