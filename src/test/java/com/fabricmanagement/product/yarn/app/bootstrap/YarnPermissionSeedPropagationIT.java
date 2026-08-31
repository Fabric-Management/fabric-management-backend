package com.fabricmanagement.product.yarn.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.bootstrap.PermissionTemplateSeeder;
import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.auth.app.onboarding.CloneTemplatePermissionsStep;
import com.fabricmanagement.platform.auth.app.onboarding.OnboardingContext;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class YarnPermissionSeedPropagationIT extends AbstractIntegrationTest {

  @Autowired private PermissionTemplateSeeder seeder;
  @Autowired private CloneTemplatePermissionsStep clonePermissionsStep;
  @Autowired private SystemTransactionExecutor systemTransactions;

  @Test
  void templateAndFreshOnboardingTenantMirrorFiberReadWriteWithoutYarnApprove() {
    seeder.seed();
    UUID freshTenant = insertTenant();
    OnboardingContext context = new OnboardingContext();
    context.setTenantId(freshTenant);
    clonePermissionsStep.execute(context);

    Set<Grant> expected =
        grants(TenantContext.TEMPLATE_TENANT_ID, "fiber").stream()
            .filter(grant -> Set.of("read", "write").contains(grant.action()))
            .collect(Collectors.toSet());

    assertThat(grants(TenantContext.TEMPLATE_TENANT_ID, "yarn")).isEqualTo(expected);
    assertThat(grants(freshTenant, "yarn")).isEqualTo(expected);
    assertThat(count(freshTenant, "yarn", "approve")).isZero();
    assertThat(count(TenantContext.TEMPLATE_TENANT_ID, "yarn", "approve")).isZero();
  }

  private UUID insertTenant() {
    UUID tenantId = UUID.randomUUID();
    String suffix = tenantId.toString().substring(0, 8);
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, status) "
                  + "VALUES (?, ?, ?, ?, 'ACTIVE')",
              tenantId,
              "YARN-PERM-" + suffix.toUpperCase(java.util.Locale.ROOT),
              "yarn-perm-" + suffix,
              "Yarn Permission " + suffix);
          return null;
        });
    return tenantId;
  }

  private Set<Grant> grants(UUID tenantId, String resource) {
    return systemTransactions.executeInTransaction(
        jdbc ->
            Set.copyOf(
                jdbc.query(
                    "SELECT role_code, department_code, action, data_scope "
                        + "FROM common_user.permission_template "
                        + "WHERE tenant_id = ? AND resource = ? AND is_active = TRUE",
                    (rs, rowNum) ->
                        new Grant(
                            rs.getString("role_code"),
                            rs.getString("department_code"),
                            rs.getString("action"),
                            rs.getString("data_scope")),
                    tenantId,
                    resource)));
  }

  private int count(UUID tenantId, String resource, String action) {
    return systemTransactions.executeInTransaction(
        jdbc ->
            jdbc.queryForObject(
                "SELECT count(*) FROM common_user.permission_template "
                    + "WHERE tenant_id = ? AND resource = ? AND action = ?",
                Integer.class,
                tenantId,
                resource,
                action));
  }

  private record Grant(String roleCode, String departmentCode, String action, String dataScope) {}
}
