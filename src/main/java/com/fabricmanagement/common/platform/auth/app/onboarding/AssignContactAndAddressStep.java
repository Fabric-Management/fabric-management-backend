package com.fabricmanagement.common.platform.auth.app.onboarding;

import com.fabricmanagement.common.platform.company.api.facade.CompanyFacade;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(3)
@Component
@RequiredArgsConstructor
@Slf4j
public class AssignContactAndAddressStep implements OnboardingStep {

  private final CompanyFacade companyFacade;

  @Override
  public void execute(OnboardingContext context) {
    if (!context.isSalesLed()) {
      return;
    }
    UUID companyId = context.getCompanyId();
    UUID tenantId = context.getTenantId();
    if (companyId == null || tenantId == null) {
      return;
    }
    companyFacade.assignCompanyAddressAndContact(
        companyId,
        tenantId,
        context.getAddress(),
        context.getCity(),
        context.getCountry(),
        context.getPhoneNumber(),
        context.getCompanyEmail());
    log.debug("AssignContactAndAddressStep: companyId={}", companyId);
  }
}
