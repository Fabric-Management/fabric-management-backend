package com.fabricmanagement.common.platform.auth.app;

import com.fabricmanagement.common.infrastructure.web.exception.ContactAlreadyRegisteredException;
import com.fabricmanagement.common.infrastructure.web.exception.TaxIdAlreadyExistsException;
import com.fabricmanagement.common.platform.auth.app.onboarding.OnboardingContext;
import com.fabricmanagement.common.platform.auth.app.onboarding.TenantOnboardingOrchestrator;
import com.fabricmanagement.common.platform.auth.dto.SelfSignupRequest;
import com.fabricmanagement.common.platform.auth.dto.TenantOnboardingRequest;
import com.fabricmanagement.common.platform.auth.dto.TenantOnboardingResponse;
import com.fabricmanagement.common.platform.company.api.facade.CompanyFacade;
import com.fabricmanagement.common.platform.user.api.facade.UserFacade;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant Onboarding Service — entry point for sales-led and self-service tenant creation.
 *
 * <p>Delegates to {@link TenantOnboardingOrchestrator} and uses only {@link CompanyFacade} and
 * {@link UserFacade} for validation. All creation logic lives in ordered {@link
 * com.fabricmanagement.common.platform.auth.app.onboarding.OnboardingStep} implementations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantOnboardingService {

  private final TenantOnboardingOrchestrator orchestrator;
  private final CompanyFacade companyFacade;
  private final UserFacade userFacade;

  /**
   * Create new tenant via sales-led process.
   *
   * @param request Tenant onboarding request
   * @return Onboarding response with token and setup URL
   */
  @Transactional
  public TenantOnboardingResponse createSalesLedTenant(TenantOnboardingRequest request) {
    log.info("Creating sales-led tenant: company={}", request.getCompanyName());
    validateTenantCreation(request.getTaxId(), request.getAdminContact());

    OnboardingContext context = new OnboardingContext();
    context.setCompanyName(request.getCompanyName());
    context.setTaxId(request.getTaxId());
    context.setCompanyType(request.getCompanyType());
    context.setAddress(request.getAddress());
    context.setCity(request.getCity());
    context.setCountry(request.getCountry());
    context.setPhoneNumber(request.getPhoneNumber());
    context.setCompanyEmail(request.getCompanyEmail());
    context.setAdminFirstName(request.getAdminFirstName());
    context.setAdminLastName(request.getAdminLastName());
    context.setAdminContact(request.getAdminContact());
    context.setAdminDepartment(request.getAdminDepartment());
    context.setSelectedOS(
        request.getSelectedOS() != null && !request.getSelectedOS().isEmpty()
            ? request.getSelectedOS()
            : List.of("FabricOS"));
    context.setTrialDays(request.getTrialDays() != null ? request.getTrialDays() : 90);
    context.setSalesLed(true);

    return orchestrator.onboard(context);
  }

  /**
   * Create new tenant via self-service signup.
   *
   * @param request Self signup request
   * @return Onboarding response with token and setup URL
   */
  @Transactional
  public TenantOnboardingResponse createSelfServiceTenant(SelfSignupRequest request) {
    log.info("Creating self-service tenant: company={}", request.getCompanyName());
    validateTenantCreation(request.getTaxId(), request.getEmail());
    if (!Boolean.TRUE.equals(request.getAcceptedTerms())) {
      throw new IllegalArgumentException("Terms and conditions must be accepted");
    }

    OnboardingContext context = new OnboardingContext();
    context.setCompanyName(request.getCompanyName());
    context.setTaxId(request.getTaxId());
    context.setCompanyType(request.getCompanyType());
    context.setAdminFirstName(request.getFirstName());
    context.setAdminLastName(request.getLastName());
    context.setAdminContact(request.getEmail());
    context.setSelectedOS(
        request.getSelectedOS() != null && !request.getSelectedOS().isEmpty()
            ? request.getSelectedOS()
            : List.of("FabricOS"));
    context.setTrialDays(14);
    context.setSalesLed(false);

    return orchestrator.onboard(context);
  }

  private void validateTenantCreation(String taxId, String contactValue) {
    if (companyFacade.existsByTaxId(taxId)) {
      throw new TaxIdAlreadyExistsException("Company with this tax ID already exists");
    }
    if (userFacade.contactExists(contactValue)) {
      throw new ContactAlreadyRegisteredException("Contact value already registered");
    }
  }
}
