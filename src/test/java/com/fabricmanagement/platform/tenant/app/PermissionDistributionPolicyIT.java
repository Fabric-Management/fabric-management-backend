package com.fabricmanagement.platform.tenant.app;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.common.infrastructure.bootstrap.PermissionTemplateBackfillRunner;
import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PermissionDistributionPolicyIT extends AbstractIntegrationTest {
  @Autowired private SystemTransactionExecutor system;
  @Autowired private PermissionTemplateBackfillRunner backfill;
  @Autowired private TenantClonerService cloner;

  @Test
  void explicitOnlyGrantCannotEscapeTemplateTenantThroughBackfillOrOnboardingClone() {
    UUID injected = UUID.randomUUID(), existing = UUID.randomUUID(), fresh = UUID.randomUUID();
    system.executeInTransaction(
        jdbc -> {
          jdbc.update(
              """
          INSERT INTO common_tenant.common_tenant (id,uid,slug,name,status)
          VALUES (?, ?, ?, 'Routing existing tenant', 'ACTIVE')
          """,
              existing,
              existing.toString(),
              existing.toString());
          jdbc.update(
              """
          INSERT INTO common_user.permission_template
            (id,tenant_id,uid,role_code,resource,action,data_scope,is_active,created_at,updated_at,version)
          VALUES (?, ?, ?, ?, 'flowboard', 'manage-routing', 'GLOBAL', true, now(), now(), 0)
          """,
              injected,
              TenantContext.TEMPLATE_TENANT_ID,
              injected.toString(),
              "RT-" + injected.toString().substring(0, 8));
          return null;
        });
    try {
      backfill.run();
      assertGrantCounts(existing);
      system.executeInTransaction(
          jdbc -> {
            jdbc.update(
                """
            INSERT INTO common_tenant.common_tenant (id,uid,slug,name,status)
            VALUES (?, ?, ?, 'Routing new tenant', 'ACTIVE')
            """,
                fresh,
                fresh.toString(),
                fresh.toString());
            return null;
          });
      assertThat(cloner.clonePermissionTemplatesToTenant(TenantContext.TEMPLATE_TENANT_ID, fresh))
          .isGreaterThan(0);
      assertGrantCounts(fresh);
      // An explicit tenant-owned grant remains supported and is never removed by distribution.
      system.executeInTransaction(
          jdbc -> {
            jdbc.update(
                """
            INSERT INTO common_user.permission_template
              (id,tenant_id,uid,role_code,resource,action,data_scope,is_active,created_at,updated_at,version)
            VALUES (gen_random_uuid(), ?, gen_random_uuid()::text, 'EXPLICIT', 'flowboard',
              'manage-routing', 'GLOBAL', true, now(), now(), 0)
            """,
                existing);
            return null;
          });
      backfill.run();
      assertThat(count(existing, "flowboard", "manage-routing")).isEqualTo(1);
    } finally {
      system.executeInTransaction(
          jdbc -> {
            jdbc.update("DELETE FROM common_user.permission_template WHERE id = ?", injected);
            return null;
          });
    }
  }

  private void assertGrantCounts(UUID tenant) {
    assertThat(count(tenant, "flowboard", "manage-routing")).isZero();
    assertThat(count(tenant, "flowboard", "write")).isGreaterThan(0);
    assertThat(count(tenant, "sales", "write")).isGreaterThan(0);
  }

  private int count(UUID tenant, String resource, String action) {
    return system.executeInTransaction(
        jdbc ->
            jdbc.queryForObject(
                "SELECT count(*) FROM common_user.permission_template WHERE tenant_id = ? AND resource = ? AND action = ?",
                Integer.class,
                tenant,
                resource,
                action));
  }
}
