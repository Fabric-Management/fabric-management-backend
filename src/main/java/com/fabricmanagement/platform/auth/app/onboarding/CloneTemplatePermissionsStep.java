package com.fabricmanagement.platform.auth.app.onboarding;

import com.fabricmanagement.platform.tenant.app.TenantClonerService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Step 10: Clone permission templates from the golden-template to the new tenant.
 *
 * <p>This step uses BYPASSRLS (SystemTransactionExecutor) to read permission templates from the
 * TEMPLATE tenant and insert them into the new tenant's scope. This ensures that non-ADMIN users
 * (managers, supervisors, workers) resolve correct permissions when they are eventually created.
 *
 * <p>Idempotent: skips cloning if the target tenant already has permission_template rows.
 *
 * <p><b>Why order 10:</b> Permission templates refer to roles and departments by string codes, so
 * there are no hard foreign key dependencies other than the tenant itself. Placed at the end of the
 * onboarding pipeline as a trailing step to avoid renumbering existing core steps.
 */
@Order(10) // Appended after SendWelcomeEmailStep (9)
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
    log.info(
        "CloneTemplatePermissionsStep: Cloned {} permission templates from TEMPLATE ({}) to new tenant ({})",
        clonedCount,
        templateTenantId,
        targetTenantId);
  }
}
