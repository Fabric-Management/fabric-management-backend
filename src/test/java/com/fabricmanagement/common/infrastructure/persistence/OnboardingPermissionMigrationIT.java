package com.fabricmanagement.common.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.bootstrap.PermissionTemplateBackfillRunner;
import com.fabricmanagement.platform.tenant.app.TenantClonerService;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

class OnboardingPermissionMigrationIT extends AbstractIntegrationTest {
  @Autowired private SystemTransactionExecutor system;
  @Autowired private PermissionTemplateBackfillRunner backfill;
  @Autowired private TenantClonerService cloner;

  @Test
  void migrationAndStartupAreIdempotentAndPreserveTenantCustomisation() throws Exception {
    UUID existing = createTenant();
    UUID customised = createTenant();
    system.executeInTransaction(
        jdbc -> {
          jdbc.update(
              """
          INSERT INTO common_user.permission_template
            (id,tenant_id,uid,role_code,resource,action,data_scope,is_active,created_at,updated_at,version)
          VALUES (gen_random_uuid(), ?, gen_random_uuid()::text, 'ADMIN', 'organization',
            'configure', 'OWN', false, now(), now(), 0)
          """,
              customised);
          // Real tenant customisation, not a rule the migration may replace or reactivate.
          return null;
        });
    String sql =
        new ClassPathResource(
                "db/migration/V20261023150000__onboarding_company_setup_permission.sql")
            .getContentAsString(StandardCharsets.UTF_8);
    system.executeInTransaction(
        jdbc -> {
          jdbc.execute(sql);
          return null;
        });
    system.executeInTransaction(
        jdbc -> {
          jdbc.execute(sql);
          return null;
        });
    backfill.run();
    backfill.run();
    assertThat(count(existing, "ADMIN")).isEqualTo(1);
    assertThat(count(existing, "WORKER")).isZero();
    assertThat(count(existing, "MANAGER")).isZero();
    system.executeInTransaction(
        jdbc -> {
          assertThat(
                  jdbc.queryForObject(
                      """
          SELECT data_scope || ':' || is_active::text FROM common_user.permission_template
          WHERE tenant_id = ? AND role_code = 'ADMIN' AND department_code IS NULL
            AND resource = 'organization' AND action = 'configure'
          """,
                      String.class,
                      customised))
              .isEqualTo("OWN:false");
          return null;
        });

    UUID fresh = createTenant();
    cloner.clonePermissionTemplatesToTenant(TenantContext.TEMPLATE_TENANT_ID, fresh);
    assertThat(count(fresh, "ADMIN")).isEqualTo(1);
    assertThat(count(fresh, "WORKER")).isZero();
  }

  private UUID createTenant() {
    UUID id = UUID.randomUUID();
    system.executeInTransaction(
        jdbc -> {
          jdbc.update(
              """
          INSERT INTO common_tenant.common_tenant (id,uid,slug,name,status)
          VALUES (?, ?, ?, 'Hawthorn Weaving Ltd', 'TRIAL')
          """,
              id,
              id.toString(),
              id.toString());
          return null;
        });
    return id;
  }

  private int count(UUID tenant, String role) {
    return system.executeInTransaction(
        jdbc ->
            jdbc.queryForObject(
                """
        SELECT count(*) FROM common_user.permission_template
        WHERE tenant_id = ? AND role_code = ? AND resource = 'organization' AND action = 'configure'
        """,
                Integer.class,
                tenant,
                role));
  }
}
