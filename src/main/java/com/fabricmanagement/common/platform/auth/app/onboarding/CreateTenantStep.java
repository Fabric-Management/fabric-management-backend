package com.fabricmanagement.common.platform.auth.app.onboarding;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.tenant.api.facade.TenantFacade;
import com.fabricmanagement.common.platform.tenant.dto.CreateTenantRequest;
import com.fabricmanagement.common.platform.tenant.dto.TenantDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Step 1: Create Tenant entity.
 *
 * <p>Creates the platform-level Tenant which defines:
 *
 * <ul>
 *   <li>Subscription and billing boundary
 *   <li>Settings (timezone, locale, currency)
 *   <li>Status (TRIAL, ACTIVE, etc.)
 * </ul>
 *
 * <p>Sets tenantId and tenantUid in context for subsequent steps.
 */
@Order(1)
@Component
@RequiredArgsConstructor
@Slf4j
public class CreateTenantStep implements OnboardingStep {

  private final TenantFacade tenantFacade;

  @Override
  public void execute(OnboardingContext context) {
    log.debug("CreateTenantStep: Creating tenant for {}", context.getCompanyName());

    CreateTenantRequest request =
        CreateTenantRequest.builder()
            .name(context.getCompanyName())
            .billingEmail(context.getCompanyEmail())
            .country(context.getCountry())
            .trialDays(context.getTrialDays() > 0 ? context.getTrialDays() : 14)
            .build();

    TenantDto tenant = tenantFacade.createTenant(request);

    // Set tenant context for subsequent steps
    context.setTenantId(tenant.getId());
    context.setTenantUid(tenant.getUid());
    TenantContext.setCurrentTenantId(tenant.getId());
    TenantContext.setCurrentTenantUid(tenant.getUid());

    log.debug("CreateTenantStep: tenantId={}, tenantUid={}", tenant.getId(), tenant.getUid());
  }
}
