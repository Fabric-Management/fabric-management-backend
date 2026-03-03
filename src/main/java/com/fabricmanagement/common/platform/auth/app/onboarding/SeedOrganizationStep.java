package com.fabricmanagement.common.platform.auth.app.onboarding;

import com.fabricmanagement.common.platform.subscription.app.TenantSeedService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Step 3: Seed default departments for the organization.
 *
 * <p>Runs before CreateAdminUserStep so the admin user can be assigned to "Administration Office".
 */
@Order(3) // After CreateOrganizationStep (2) — departments must exist before admin user
@Component
@RequiredArgsConstructor
@Slf4j
public class SeedOrganizationStep implements OnboardingStep {

  private final TenantSeedService tenantSeedService;

  @Override
  public void execute(OnboardingContext context) {
    UUID tenantId = context.getTenantId();
    UUID organizationId = context.getOrganizationId();
    if (tenantId == null || organizationId == null) {
      return;
    }
    tenantSeedService.seedDepartments(tenantId, organizationId);
    log.debug("SeedOrganizationStep: tenantId={}, organizationId={}", tenantId, organizationId);
  }
}
