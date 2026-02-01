package com.fabricmanagement.common.platform.auth.app.onboarding;

import com.fabricmanagement.common.platform.company.api.facade.CompanyFacade;
import com.fabricmanagement.common.platform.company.dto.CompanyDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(1)
@Component
@RequiredArgsConstructor
@Slf4j
public class CreateCompanyStep implements OnboardingStep {

  private final CompanyFacade companyFacade;

  @Override
  public void execute(OnboardingContext context) {
    CompanyDto company = companyFacade.createTenantCompany(context.toCreateTenantCompanyRequest());
    context.setCompanyId(company.getId());
    context.setTenantId(company.getTenantId());
    context.setCompanyUid(company.getUid());
    context.setCompanyName(company.getCompanyName());
    log.debug(
        "CreateCompanyStep: companyId={}, tenantId={}", company.getId(), company.getTenantId());
  }
}
