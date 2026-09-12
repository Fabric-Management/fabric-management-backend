package com.fabricmanagement.common.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.bootstrap.PermissionTemplateBackfillRunner;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.tenant.app.TenantClonerService;
import com.fabricmanagement.platform.user.app.PermissionManagementService;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.dto.UpdatePermissionTemplateRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * PERM-SEED-1: the backfill must repair tenants that were created while the seeder was skipping its
 * catalogue, and must do so without a tenant context (production boot has none).
 *
 * <p>Replaces {@code FinancePermissionMigrationIT}, which covered the single-resource finance
 * backfill. The generic runner subsumes it: {@code finance:*} lives in the template tenant like
 * every other rule, so a tenant that receives the template receives finance.
 *
 * <p>Note: Testcontainers connects as the database owner, which bypasses RLS. This test therefore
 * proves the SQL is correct, not that RLS was navigated. The `fabric_system` path is exercised in
 * production only — see {@code rls_onboarding_and_test_gap}.
 */
@SpringBootTest(properties = {"spring.flyway.enabled=true"})
@ActiveProfiles("test")
@Testcontainers
class PermissionTemplateBackfillIT {

  private static final String TEMPLATE_TENANT = "00000000-0000-0000-ffff-000000000001";

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.2-alpine"))
          .withDatabaseName("fabric_test")
          .withUsername("fabric_owner")
          .withPassword("fabric123");

  @DynamicPropertySource
  static void registerPgProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
  }

  @Autowired private SystemTransactionExecutor systemTransactionExecutor;
  @Autowired private PermissionTemplateBackfillRunner backfillRunner;
  @Autowired private PermissionEvaluator permissionEvaluator;
  @Autowired private TenantClonerService tenantClonerService;
  @Autowired private PermissionManagementService permissionManagementService;
  @Autowired private CacheManager cacheManager;

  private static final UUID RETIREMENT_TENANT =
      UUID.fromString("ca720000-0000-4000-8000-000000000010");
  private static final UUID BACKFILL_TENANT =
      UUID.fromString("ca720000-0000-4000-8000-000000000011");
  private static final UUID CLONE_TENANT = UUID.fromString("ca720000-0000-4000-8000-000000000012");
  private static final String RETIREMENT_ROLE = "PC2_RETIREMENT";
  private static final String RETIREMENT_DEPARTMENT = "PC2";

  @Test
  void bringsCrippledTenantsUpToTheTemplateWithoutTenantContext() {
    String crippled = "11111111-1111-1111-1111-111111111111";
    String healthy = "22222222-2222-2222-2222-222222222222";

    // A tenant onboarded while the seeder was poisoned: it holds only the rows that
    // V20260706120000 wrote, cloned faithfully. This is what real signups received.
    systemTransactionExecutor.executeInTransaction(
        jdbcTemplate -> {
          jdbcTemplate.update(
              """
              INSERT INTO common_tenant.common_tenant (id, uid, slug, name, status)
              VALUES (?::uuid, 'T-CRIPPLED', 'crippled', 'Crippled Mills', 'ACTIVE'),
                     (?::uuid, 'T-HEALTHY', 'healthy', 'Healthy Mills', 'ACTIVE')
              """,
              crippled,
              healthy);
          jdbcTemplate.update(
              """
              INSERT INTO common_user.permission_template (
                  id, tenant_id, uid, role_code, department_code, resource, action,
                  data_scope, is_active, created_at, updated_at
              ) VALUES
              (gen_random_uuid(), ?::uuid, gen_random_uuid()::varchar, 'MANAGER', NULL, 'sales', 'approve', 'ORGANIZATION', true, NOW(), NOW())
              """,
              crippled);
          return null;
        });

    backfillRunner.run();

    // sales:read is the permission whose absence produced the 403 that started this investigation.
    assertThat(countOf(crippled, "sales", "read"))
        .withFailMessage("crippled tenant should have received sales:read from the template")
        .isGreaterThan(0);
    assertThat(countOf(crippled, "fiber", "read")).isGreaterThan(0);
    assertThat(countOf(crippled, "finance", "write"))
        .withFailMessage("the generic backfill must subsume the old finance-only backfill")
        .isGreaterThan(0);

    // COLOR-RBAC-1: the dedicated colours resource reaches existing tenants through the same path.
    assertThat(countOf(crippled, "colors", "read")).isGreaterThan(0);
    assertThat(countOf(crippled, "colors", "write")).isGreaterThan(0);
    assertThat(countOf(crippled, "colors", "approve")).isGreaterThan(0);
    assertThat(countOf(crippled, "colors", "manage")).isGreaterThan(0);

    // QC-RELEASE-1a: the canonical template/backfill path carries the complete quality matrix.
    assertThat(countOf(crippled, "quality", "read")).isGreaterThan(0);
    assertThat(countOf(crippled, "quality", "write")).isGreaterThan(0);
    assertThat(countOf(crippled, "quality", "approve")).isGreaterThan(0);
    assertThat(countOf(crippled, "quality", "manage")).isGreaterThan(0);

    // OWN-PERM-1: existing tenants receive both least-privilege ownership grants.
    assertThat(countGrant(crippled, "ADMIN", null, "sales", "assign-owner")).isEqualTo(1);
    assertThat(countGrant(crippled, "MANAGER", "SALES", "sales", "assign-owner")).isEqualTo(1);
    assertThat(countGrant(crippled, "SUPERVISOR", "SALES", "sales", "assign-owner")).isZero();
    assertThat(countGrant(crippled, "WORKER", "SALES", "sales", "assign-owner")).isZero();

    // A tenant that never existed before the fix still gets the full set.
    assertThat(countOf(healthy, "sales", "read")).isGreaterThan(0);

    // Every tenant now mirrors the template, row for row.
    assertThat(templateCount(crippled)).isEqualTo(templateCount(TEMPLATE_TENANT));
    assertThat(templateCount(healthy)).isEqualTo(templateCount(TEMPLATE_TENANT));

    // PERM-CAT-1: all six previously unseeded pairs reach both existing-tenant scenarios.
    for (String tenant : java.util.List.of(crippled, healthy)) {
      assertThat(countGrant(tenant, "SUPERVISOR", "WEAVING", "production", "read")).isEqualTo(1);
      assertThat(countGrant(tenant, "SUPERVISOR", "WEAVING", "production", "write")).isEqualTo(1);
      assertThat(countGrant(tenant, "WORKER", "FINANCE", "costing", "read")).isEqualTo(1);
      assertThat(countGrant(tenant, "WORKER", "FINANCE", "costing", "write")).isEqualTo(1);
      assertThat(countGrant(tenant, "MANAGER", "FINANCE", "costing", "manage")).isEqualTo(1);
      assertThat(countGrant(tenant, "MANAGER", "WAREHOUSE", "logistics", "delete")).isEqualTo(1);
      assertThat(countGrant(tenant, "WORKER", "FINANCE", "costing", "manage")).isZero();
      assertThat(countGrant(tenant, "SUPERVISOR", "WAREHOUSE", "logistics", "delete")).isZero();
      assertThat(countGrant(tenant, "MANAGER", "LOGISTICS", "logistics", "delete")).isZero();
      // PERM-CAT-2: retired grants never become active in backfilled tenants.
      assertThat(countOf(tenant, "dashboard", "view")).isZero();
      assertThat(countOf(tenant, "settings", "view")).isZero();
      assertThat(countOf(tenant, "flowboard", "view")).isZero();
      assertThat(countOf(tenant, "flowboard", "manage")).isZero();
      assertThat(countOf(tenant, "admin", "access")).isZero();
    }

    // Partner roles are cloned as declared, and none of them may touch finance.
    Integer partnerFinance =
        systemTransactionExecutor.executeInTransaction(
            jdbcTemplate ->
                jdbcTemplate.queryForObject(
                    """
                    SELECT count(*) FROM common_user.permission_template
                    WHERE role_code LIKE 'PARTNER_%' AND resource = 'finance'
                    """,
                    Integer.class));
    assertThat(partnerFinance)
        .withFailMessage("PARTNER roles must not have 'finance' access")
        .isZero();
  }

  @Test
  void isIdempotentAcrossBoots() {
    String existingTenant = "33333333-3333-3333-3333-333333333333";
    systemTransactionExecutor.executeInTransaction(
        jdbcTemplate -> {
          jdbcTemplate.update(
              """
              INSERT INTO common_tenant.common_tenant (id, uid, slug, name, status)
              VALUES (?::uuid, 'T-IDEMPOTENT', 'idempotent', 'Idempotent Mills', 'ACTIVE')
              """,
              existingTenant);
          return null;
        });

    backfillRunner.run();
    assertThat(countGrant(existingTenant, "ADMIN", null, "sales", "assign-owner")).isEqualTo(1);
    assertThat(countGrant(existingTenant, "MANAGER", "SALES", "sales", "assign-owner"))
        .isEqualTo(1);
    Integer before = totalRows();

    backfillRunner.run();

    assertThat(totalRows()).withFailMessage("backfill must not duplicate rows").isEqualTo(before);
    assertThat(countGrant(existingTenant, "ADMIN", null, "sales", "assign-owner")).isEqualTo(1);
    assertThat(countGrant(existingTenant, "MANAGER", "SALES", "sales", "assign-owner"))
        .isEqualTo(1);
  }

  @Test
  void retiredPermissionsCannotBeEvaluatedBackfilledClonedOrReactivated() {
    UUID user = UUID.randomUUID();
    UUID retiredRow = UUID.randomUUID();
    try {
      systemTransactionExecutor.executeInTransaction(
          jdbc -> {
            createRetirementTenant(jdbc, RETIREMENT_TENANT);
            seedRetirementUser(jdbc, user);
            for (UUID tenant : List.of(UUID.fromString(TEMPLATE_TENANT), RETIREMENT_TENANT)) {
              insertRetirementTemplate(
                  jdbc,
                  tenant.equals(RETIREMENT_TENANT) ? retiredRow : UUID.randomUUID(),
                  tenant,
                  "projects",
                  "read",
                  null);
              insertRetirementTemplate(jdbc, UUID.randomUUID(), tenant, "sales", "read", null);
            }
            // State 3 would still be selected by the cloner unless the migration closes it.
            insertRetirementTemplate(
                jdbc,
                UUID.randomUUID(),
                UUID.fromString(TEMPLATE_TENANT),
                "dashboard",
                "view",
                Timestamp.valueOf("2025-09-12 10:00:00"));
            return null;
          });
      assertThat(retirementCan(user, "projects", "read")).isTrue();
      assertThat(retirementCan(user, "sales", "read")).isTrue();
      Map<String, Object> keptBefore = retirementRow("sales", "read");

      runRetirementMigration();
      assertThat(retirementCan(user, "projects", "read")).isFalse();
      assertThat(retirementCan(user, "sales", "read")).isTrue();
      assertThat(retirementRow("sales", "read")).isEqualTo(keptBefore);

      systemTransactionExecutor.executeInTransaction(
          jdbc -> {
            createRetirementTenant(jdbc, BACKFILL_TENANT);
            return null;
          });
      clearPermissionsCache();
      backfillRunner.run();
      assertThat(countOf(BACKFILL_TENANT.toString(), "sales", "read")).isGreaterThan(0);
      assertNoRetiredRows(BACKFILL_TENANT);

      // Create D after the cross-tenant backfill, or the cloner's any-row guard skips the copy.
      systemTransactionExecutor.executeInTransaction(
          jdbc -> {
            createRetirementTenant(jdbc, CLONE_TENANT);
            return null;
          });
      clearPermissionsCache();
      Integer beforeClone =
          systemTransactionExecutor.executeInTransaction(
              jdbc ->
                  jdbc.queryForObject(
                      "SELECT count(*) FROM common_user.permission_template WHERE tenant_id = ?",
                      Integer.class,
                      CLONE_TENANT));
      assertThat(beforeClone).isZero();
      assertThat(
              tenantClonerService.clonePermissionTemplatesToTenant(
                  UUID.fromString(TEMPLATE_TENANT), CLONE_TENANT))
          .isGreaterThan(0);
      assertThat(countOf(CLONE_TENANT.toString(), "sales", "read")).isGreaterThan(0);
      assertNoRetiredRows(CLONE_TENANT);

      Map<String, Object> retiredBefore = retirementRow("projects", "read");
      var request = new UpdatePermissionTemplateRequest();
      request.setDataScope(DataScope.ORGANIZATION);
      request.setIsActive(true);
      assertThatThrownBy(
              () ->
                  TenantContext.executeInTenantContext(
                      RETIREMENT_TENANT,
                      () ->
                          permissionManagementService.updateTemplate(
                              RETIREMENT_TENANT, retiredRow, request)))
          .isInstanceOfSatisfying(
              PlatformDomainException.class,
              error -> {
                assertThat(error.getHttpStatus()).isEqualTo(410);
                assertThat(error.getErrorCode()).isEqualTo("GONE");
              });
      assertThat(retirementRow("projects", "read")).isEqualTo(retiredBefore);
      assertThat(retirementCan(user, "projects", "read")).isFalse();
    } finally {
      cleanupRetirementFixture();
      clearPermissionsCache();
    }
  }

  private void runRetirementMigration() {
    String script;
    try (var input =
        new ClassPathResource("db/migration/V20260912120000__retire_stale_permission_pairs.sql")
            .getInputStream()) {
      script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException("Could not read permission retirement migration", exception);
    }
    systemTransactionExecutor.executeInTransaction(
        jdbc -> {
          jdbc.execute(script);
          return null;
        });
    clearPermissionsCache();
  }

  private boolean retirementCan(UUID user, String resource, String action) {
    clearPermissionsCache();
    return TenantContext.executeInTenantContext(
        RETIREMENT_TENANT,
        () ->
            permissionEvaluator
                .evaluate(RETIREMENT_TENANT, RETIREMENT_ROLE, List.of(RETIREMENT_DEPARTMENT), user)
                .can(resource, action));
  }

  private void clearPermissionsCache() {
    var cache = cacheManager.getCache("permissions");
    assertThat(cache).isNotNull();
    cache.clear();
  }

  private void createRetirementTenant(JdbcTemplate jdbc, UUID tenant) {
    jdbc.update(
        "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, status) "
            + "VALUES (?, ?, ?, 'Permission retirement fixture', 'ACTIVE')",
        tenant,
        "PC2-" + tenant,
        "pc2-" + tenant);
  }

  private void seedRetirementUser(JdbcTemplate jdbc, UUID user) {
    UUID organization = UUID.randomUUID();
    UUID department = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO common_company.common_organization (id, tenant_id, uid, name, tax_id) "
            + "VALUES (?, ?, ?, 'Permission retirement fixture', ?)",
        organization,
        RETIREMENT_TENANT,
        "PC2-ORG-" + organization,
        organization.toString());
    jdbc.update(
        "INSERT INTO common_company.common_department "
            + "(id, tenant_id, uid, organization_id, department_name, department_code) "
            + "VALUES (?, ?, ?, ?, 'Permission retirement fixture', ?)",
        department,
        RETIREMENT_TENANT,
        "PC2-DEPT-" + department,
        organization,
        RETIREMENT_DEPARTMENT);
    jdbc.update(
        "INSERT INTO common_user.common_user "
            + "(id, tenant_id, uid, first_name, last_name, organization_id) "
            + "VALUES (?, ?, ?, 'Permission', 'Fixture', ?)",
        user,
        RETIREMENT_TENANT,
        "PC2-USER-" + user,
        organization);
    jdbc.update(
        "INSERT INTO common_user.common_user_department "
            + "(user_id, department_id, tenant_id, is_primary) VALUES (?, ?, ?, true)",
        user,
        department,
        RETIREMENT_TENANT);
  }

  private void insertRetirementTemplate(
      JdbcTemplate jdbc,
      UUID id,
      UUID tenant,
      String resource,
      String action,
      Timestamp deletedAt) {
    jdbc.update(
        "INSERT INTO common_user.permission_template "
            + "(id, tenant_id, uid, role_code, department_code, resource, action, data_scope, "
            + "is_active, deleted_at, created_at, updated_at, version) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, 'OWN', true, ?, NOW(), NOW(), 0)",
        id,
        tenant,
        "PC2-" + id,
        RETIREMENT_ROLE,
        RETIREMENT_DEPARTMENT,
        resource,
        action,
        deletedAt);
  }

  private Map<String, Object> retirementRow(String resource, String action) {
    return systemTransactionExecutor.executeInTransaction(
        jdbc ->
            jdbc.queryForMap(
                "SELECT * FROM common_user.permission_template "
                    + "WHERE tenant_id = ? AND role_code = ? AND resource = ? AND action = ?",
                RETIREMENT_TENANT,
                RETIREMENT_ROLE,
                resource,
                action));
  }

  private void assertNoRetiredRows(UUID tenant) {
    // Literal oracle, independent of PermissionKey (where these pairs have already disappeared).
    List<String> retired =
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
    List<String> actual =
        systemTransactionExecutor.executeInTransaction(
            jdbc ->
                jdbc.queryForList(
                    "SELECT resource || ':' || action FROM common_user.permission_template WHERE"
                        + " tenant_id = ?",
                    String.class,
                    tenant));
    assertThat(actual).doesNotContainAnyElementsOf(retired);
  }

  private void cleanupRetirementFixture() {
    systemTransactionExecutor.executeInTransaction(
        jdbc -> {
          // The backfill can copy our custom source row into other tests' tenants too.
          jdbc.update(
              "DELETE FROM common_user.permission_template WHERE role_code = ?", RETIREMENT_ROLE);
          for (UUID tenant : List.of(RETIREMENT_TENANT, BACKFILL_TENANT, CLONE_TENANT)) {
            jdbc.update("DELETE FROM common_user.permission_template WHERE tenant_id = ?", tenant);
            jdbc.update(
                "DELETE FROM common_user.common_user_department WHERE tenant_id = ?", tenant);
            jdbc.update("DELETE FROM common_user.common_user WHERE tenant_id = ?", tenant);
            jdbc.update("DELETE FROM common_company.common_department WHERE tenant_id = ?", tenant);
            jdbc.update(
                "DELETE FROM common_company.common_organization WHERE tenant_id = ?", tenant);
            jdbc.update("DELETE FROM common_tenant.common_tenant WHERE id = ?", tenant);
          }
          return null;
        });
  }

  private Integer countOf(String tenantId, String resource, String action) {
    return systemTransactionExecutor.executeInTransaction(
        jdbcTemplate ->
            jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM common_user.permission_template
                WHERE tenant_id = ?::uuid AND resource = ? AND action = ? AND is_active = true
                """,
                Integer.class,
                tenantId,
                resource,
                action));
  }

  private Integer countGrant(
      String tenantId, String roleCode, String departmentCode, String resource, String action) {
    return systemTransactionExecutor.executeInTransaction(
        jdbcTemplate ->
            jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM common_user.permission_template
                WHERE tenant_id = ?::uuid
                  AND role_code = ?
                  AND COALESCE(department_code, '__ALL__') =
                      COALESCE(?::varchar, '__ALL__')
                  AND resource = ?
                  AND action = ?
                """,
                Integer.class,
                tenantId,
                roleCode,
                departmentCode,
                resource,
                action));
  }

  private Integer templateCount(String tenantId) {
    return systemTransactionExecutor.executeInTransaction(
        jdbcTemplate ->
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM common_user.permission_template "
                    + "WHERE tenant_id = ?::uuid AND is_active = true AND deleted_at IS NULL",
                Integer.class,
                tenantId));
  }

  private Integer totalRows() {
    return systemTransactionExecutor.executeInTransaction(
        jdbcTemplate ->
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM common_user.permission_template", Integer.class));
  }
}
