package com.fabricmanagement.common.platform.auth.app.onboarding;

import com.fabricmanagement.common.platform.company.api.facade.CompanyFacade;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(5)
@Component
@RequiredArgsConstructor
@Slf4j
public class SeedOrganizationStep implements OnboardingStep {

  private final CompanyFacade companyFacade;

  @Override
  public void execute(OnboardingContext context) {
    UUID tenantId = context.getTenantId();
    UUID companyId = context.getCompanyId();
    if (tenantId == null || companyId == null) {
      return;
    }
    companyFacade.seedDepartmentsAndPositions(tenantId, companyId);
    log.debug("SeedOrganizationStep: tenantId={}, companyId={}", tenantId, companyId);
  }
}
