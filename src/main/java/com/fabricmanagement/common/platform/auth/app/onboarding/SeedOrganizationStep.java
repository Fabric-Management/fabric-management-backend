package com.fabricmanagement.common.platform.auth.app.onboarding;

import com.fabricmanagement.common.platform.subscription.app.TenantSeedService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Step 6: Seed default departments and positions for the organization.
 *
 * <p>Uses TenantSeedService directly for seeding organizational structure.
 */
@Order(6) // After CreateSubscriptionsStep (5)
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
    tenantSeedService.seedDepartmentsAndPositions(tenantId, organizationId);
    log.debug("SeedOrganizationStep: tenantId={}, organizationId={}", tenantId, organizationId);
  }
}
