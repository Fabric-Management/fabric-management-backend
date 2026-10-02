package com.fabricmanagement.platform.auth.app.onboarding;

import com.fabricmanagement.platform.tenant.app.TenantClonerService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Step 10: Clone permission templates and the sales ownership policy from the golden-template to
 * the new tenant. Both are template-owned governance every later step and listener reads: without
 * the ownership policy, every customer relationship the tenant (or its demo data) establishes fails
 * in the async ownership listener. The legacy playground clone and start-up reference provisioning
 * already copy the policy; self-service onboarding did not.
 *
 * <p>This step uses BYPASSRLS (SystemTransactionExecutor) to read permission templates from the
 * TEMPLATE tenant and insert them into the new tenant's scope. This ensures that non-ADMIN users
 * (managers, supervisors, workers) resolve correct permissions when they are eventually created.
 *
 * <p>Idempotent: skips cloning if the target tenant already has permission_template rows.
 *
 * <p><b>Why order 10:</b> Permission templates refer to roles and departments by string codes, so
 * there are no hard foreign key dependencies other than the tenant itself. Runs before demo seed
 * and post-commit signup email publication so the tenant is ready before any setup link is sent.
 */
@Order(10)
@Component
@RequiredArgsConstructor
@Slf4j
public class CloneTemplatePermissionsStep implements OnboardingStep {

  private final TenantClonerService tenantClonerService;

  @Override
  public void execute(OnboardingContext context) {
    UUID targetTenantId = context.getTenantId();
    if (targetTenantId == null) {
      log.warn(
          "CloneTemplatePermissionsStep: tenantId is null, skipping permission template cloning.");
      return;
    }

    UUID templateTenantId = tenantClonerService.findTemplateTenantId();
    if (templateTenantId == null) {
      log.warn(
          "CloneTemplatePermissionsStep: No TEMPLATE tenant found. "
              + "Permission templates will not be pre-seeded. Non-ADMIN users will lack permissions.");
      return;
    }

    int clonedCount =
        tenantClonerService.clonePermissionTemplatesToTenant(templateTenantId, targetTenantId);
    int ownershipPolicies = tenantClonerService.cloneOwnershipPolicyToTenant(targetTenantId);
    log.info(
        "CloneTemplatePermissionsStep: ownership policy provisioned={} for tenant ({})",
        ownershipPolicies,
        targetTenantId);
    log.info(
        "CloneTemplatePermissionsStep: Cloned {} permission templates from TEMPLATE ({}) to new tenant ({})",
        clonedCount,
        templateTenantId,
        targetTenantId);
  }
}
