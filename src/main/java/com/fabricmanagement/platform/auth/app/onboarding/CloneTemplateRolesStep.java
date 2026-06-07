package com.fabricmanagement.platform.auth.app.onboarding;

import com.fabricmanagement.platform.tenant.app.TenantClonerService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Step 3: Clone platform roles from TEMPLATE tenant to the new tenant.
 *
 * <p>This step uses BYPASSRLS (SystemTransactionExecutor) to read roles from the TEMPLATE tenant
 * and insert them into the new tenant's scope. Must run BEFORE CreateAdminUserStep because admin
 * user creation requires an ADMIN role to exist in the target tenant.
 *
 * <p>Idempotent: skips roles that already exist in the target tenant (by role_code).
 *
 * <p><b>Why this is needed:</b> Per-tenant role model requires each tenant to have its own copy of
 * platform roles. Without this step, {@code RoleService.findTenantAdminRoleOrThrow()} fails because
 * no roles exist for the newly created tenant.
 */
@Order(3) // After CreateOrganizationStep (2), before SeedOrganizationStep (4)
@Component
@RequiredArgsConstructor
@Slf4j
public class CloneTemplateRolesStep implements OnboardingStep {

  private final TenantClonerService tenantClonerService;

  @Override
  public void execute(OnboardingContext context) {
    UUID targetTenantId = context.getTenantId();
    if (targetTenantId == null) {
      log.warn("CloneTemplateRolesStep: tenantId is null, skipping role cloning.");
      return;
    }

    UUID templateTenantId = tenantClonerService.findTemplateTenantId();
    if (templateTenantId == null) {
      log.warn(
          "CloneTemplateRolesStep: No TEMPLATE tenant found. "
              + "Roles will not be pre-seeded. Admin user creation may fail.");
      return;
    }

    int clonedCount = tenantClonerService.cloneRolesToTenant(templateTenantId, targetTenantId);
    log.info(
        "CloneTemplateRolesStep: Cloned {} roles from TEMPLATE ({}) to new tenant ({})",
        clonedCount,
        templateTenantId,
        targetTenantId);
  }
}
