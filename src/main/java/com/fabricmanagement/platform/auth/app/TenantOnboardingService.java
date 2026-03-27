package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.common.infrastructure.web.exception.ContactAlreadyRegisteredException;
import com.fabricmanagement.common.infrastructure.web.exception.TaxIdAlreadyExistsException;
import com.fabricmanagement.platform.auth.app.onboarding.OnboardingContext;
import com.fabricmanagement.platform.auth.app.onboarding.TenantOnboardingOrchestrator;
import com.fabricmanagement.platform.auth.dto.SelfSignupRequest;
import com.fabricmanagement.platform.auth.dto.TenantOnboardingRequest;
import com.fabricmanagement.platform.auth.dto.TenantOnboardingResponse;
import com.fabricmanagement.platform.organization.api.facade.OrganizationFacade;
import com.fabricmanagement.platform.user.api.facade.UserFacade;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant Onboarding Service — entry point for sales-led and self-service tenant creation.
 *
 * <p>Delegates to {@link TenantOnboardingOrchestrator} and uses only {@link OrganizationFacade} and
 * {@link UserFacade} for validation. All creation logic lives in ordered {@link
 * com.fabricmanagement.platform.auth.app.onboarding.OnboardingStep} implementations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantOnboardingService {

  /** Default trial days for self-service signups. */
  private static final int DEFAULT_SELF_SERVICE_TRIAL_DAYS = 14;

  /** Default trial days for sales-led onboarding when not specified. */
  private static final int DEFAULT_SALES_LED_TRIAL_DAYS = 90;

  private final TenantOnboardingOrchestrator orchestrator;
  private final OrganizationFacade organizationFacade;
  private final UserFacade userFacade;

  /**
   * Create new tenant via sales-led process.
   *
   * @param request Tenant onboarding request
   * @return Onboarding response with token and setup URL
   */
  @Transactional
  public TenantOnboardingResponse createSalesLedTenant(TenantOnboardingRequest request) {
    log.info("Creating sales-led tenant: organization={}", request.getOrganizationName());
    validateTenantCreation(request.getTaxId(), request.getAdminContact());

    OnboardingContext context = new OnboardingContext();
    context.setOrganizationName(request.getOrganizationName());
    context.setTaxId(request.getTaxId());
    context.setOrganizationType(request.getOrganizationType());
    context.setAddress(request.getAddress());
    context.setCity(request.getCity());
    context.setState(request.getState());
    context.setDistrict(request.getDistrict());
    context.setPostalCode(request.getPostalCode());
    context.setCountry(request.getCountry());
    context.setPhoneNumber(request.getPhoneNumber());
    context.setOrganizationEmail(request.getOrganizationEmail());
    context.setAdminFirstName(request.getAdminFirstName());
    context.setAdminLastName(request.getAdminLastName());
    context.setAdminContact(request.getAdminContact());
    context.setSelectedOS(
        request.getSelectedOS() != null && !request.getSelectedOS().isEmpty()
            ? request.getSelectedOS()
            : List.of("FabricOS"));
    context.setTrialDays(
        request.getTrialDays() != null ? request.getTrialDays() : DEFAULT_SALES_LED_TRIAL_DAYS);
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
    log.info("Creating self-service tenant: organization={}", request.getOrganizationName());
    validateTenantCreation(request.getTaxId(), request.getEmail());
    if (!Boolean.TRUE.equals(request.getAcceptedTerms())) {
      throw new IllegalArgumentException("Terms and conditions must be accepted");
    }

    OnboardingContext context = new OnboardingContext();
    context.setOrganizationName(request.getOrganizationName());
    context.setTaxId(request.getTaxId());
    context.setOrganizationType(request.getOrganizationType());
    context.setAdminFirstName(request.getFirstName());
    context.setAdminLastName(request.getLastName());
    context.setAdminContact(request.getEmail());
    // Use admin email as billing email for self-service (no separate organization email
    // provided)
    context.setOrganizationEmail(request.getEmail());
    context.setSelectedOS(
        request.getSelectedOS() != null && !request.getSelectedOS().isEmpty()
            ? request.getSelectedOS()
            : List.of("FabricOS"));
    context.setTrialDays(DEFAULT_SELF_SERVICE_TRIAL_DAYS);
    context.setSalesLed(false);

    return orchestrator.onboard(context);
  }

  private void validateTenantCreation(String taxId, String contactValue) {
    if (organizationFacade.existsByTaxId(taxId)) {
      throw new TaxIdAlreadyExistsException("Organization with this tax ID already exists");
    }
    if (userFacade.contactExists(contactValue)) {
      throw new ContactAlreadyRegisteredException("Contact value already registered");
    }
  }
}
