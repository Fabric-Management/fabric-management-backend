package com.fabricmanagement.common.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.bootstrap.PermissionTemplateBackfillRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

    // A tenant that never existed before the fix still gets the full set.
    assertThat(countOf(healthy, "sales", "read")).isGreaterThan(0);

    // Every tenant now mirrors the template, row for row.
    assertThat(templateCount(crippled)).isEqualTo(templateCount(TEMPLATE_TENANT));
    assertThat(templateCount(healthy)).isEqualTo(templateCount(TEMPLATE_TENANT));

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
    backfillRunner.run();
    Integer before = totalRows();
    backfillRunner.run();
    assertThat(totalRows()).withFailMessage("backfill must not duplicate rows").isEqualTo(before);
  }

  private Integer countOf(String tenantId, String resource, String action) {
    return systemTransactionExecutor.executeInTransaction(
        jdbcTemplate ->
            jdbcTemplate.queryForObject(
                """
                SELECT count(*) FROM common_user.permission_template
                WHERE tenant_id = ?::uuid AND resource = ? AND action = ?
                """,
                Integer.class,
                tenantId,
                resource,
                action));
  }

  private Integer templateCount(String tenantId) {
    return systemTransactionExecutor.executeInTransaction(
        jdbcTemplate ->
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM common_user.permission_template WHERE tenant_id = ?::uuid",
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
