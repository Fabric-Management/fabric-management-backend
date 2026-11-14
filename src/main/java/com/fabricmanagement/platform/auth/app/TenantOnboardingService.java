package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.infrastructure.web.exception.ContactAlreadyRegisteredException;
import com.fabricmanagement.common.infrastructure.web.exception.SignupAlreadyPendingException;
import com.fabricmanagement.common.infrastructure.web.exception.TaxIdAlreadyExistsException;
import com.fabricmanagement.platform.auth.app.onboarding.OnboardingContext;
import com.fabricmanagement.platform.auth.app.onboarding.TenantOnboardingOrchestrator;
import com.fabricmanagement.platform.auth.domain.PendingSignup;
import com.fabricmanagement.platform.auth.domain.PendingSignupRequest;
import com.fabricmanagement.platform.auth.dto.SignupIntent;
import com.fabricmanagement.platform.auth.dto.TenantOnboardingRequest;
import com.fabricmanagement.platform.auth.dto.TenantOnboardingResponse;
import com.fabricmanagement.platform.organization.api.facade.OrganizationFacade;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant Onboarding Service — entry point for sales-led and (verified) self-service tenant
 * creation.
 *
 * <p>Delegates to {@link TenantOnboardingOrchestrator} and uses {@link OrganizationFacade}, {@link
 * IdentityProvisioningService} and {@link TenantQueryPort} for validation. All creation logic lives
 * in ordered {@link com.fabricmanagement.platform.auth.app.onboarding.OnboardingStep}
 * implementations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantOnboardingService {

  /** Default trial days for self-service signups. */
  private static final int DEFAULT_SELF_SERVICE_TRIAL_DAYS = 90;

  /** Default trial days for sales-led onboarding when not specified. */
  private static final int DEFAULT_SALES_LED_TRIAL_DAYS = 90;

  private final TenantOnboardingOrchestrator orchestrator;
  private final OrganizationFacade organizationFacade;
  private final IdentityProvisioningService identityProvisioningService;
  private final TenantQueryPort tenantQueryPort;

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
    context.setSignupSelectedOS(request.getSelectedOS());
    context.setSelectedOS(
        request.getSelectedOS() != null && !request.getSelectedOS().isEmpty()
            ? request.getSelectedOS()
            : List.of("FabricOS"));
    context.setSignupIntent("SALES_LED");
    context.setTrialDays(
        request.getTrialDays() != null ? request.getTrialDays() : DEFAULT_SALES_LED_TRIAL_DAYS);
    context.setSalesLed(true);
    context.setDemoMode(false);

    return orchestrator.onboard(context);
  }

  /**
   * Builds the company of a self-service signup whose e-mail address has been proved
   * (SIGNUP-DEFER-1 / TD-18). Called only when the e-mailed link is used: until then a signup is a
   * small {@link PendingSignup} row and nothing else.
   *
   * <p>The signup id of the evidence is the pending signup's id, so the lead recorded when the form
   * was sent is the one that gets linked to this tenant. No registration token is created and no
   * "added to a company" mail follows: the person's login is created by the same transaction.
   *
   * @return the created tenant, organisation and admin user
   */
  @Transactional
  public TenantOnboardingResponse createVerifiedSelfServiceTenant(PendingSignup signup) {
    PendingSignupRequest form = signup.getRequest();
    SignupIntent intent = SignupIntent.from(form.intent());
    log.info("Creating self-service tenant: pendingSignupId={}, intent={}", signup.getId(), intent);

    OnboardingContext context = new OnboardingContext();
    context.setSignupId(signup.getId());
    context.setTermsAcceptedAt(signup.getTermsAcceptedAt());
    context.setOrganizationName(form.organizationName());
    context.setTaxId(signup.getTaxId());
    context.setOrganizationType(form.organizationType());
    context.setAdminFirstName(form.firstName());
    context.setAdminLastName(form.lastName());
    context.setAdminContact(signup.getEmail());
    context.setSignupSelectedOS(form.selectedOS());
    // Use admin email as billing email for self-service (no separate organization email
    // provided)
    context.setOrganizationEmail(signup.getEmail());
    context.setSelectedOS(
        form.selectedOS() != null && !form.selectedOS().isEmpty()
            ? form.selectedOS()
            : List.of("FabricOS"));
    context.setSignupIntent(intent.name());
    context.setTrialDays(DEFAULT_SELF_SERVICE_TRIAL_DAYS);
    context.setSalesLed(false);
    context.setDemoMode(intent != SignupIntent.TRIAL);
    context.setInvitationEmailSuppressed(true);

    return orchestrator.onboard(context);
  }

  private void validateTenantCreation(String taxId, String contactValue) {
    if (organizationFacade.existsByTaxId(taxId)) {
      throw new TaxIdAlreadyExistsException("Organization with this tax ID already exists");
    }
    if (identityProvisioningService.isEmailRegistered(contactValue)) {
      throw new ContactAlreadyRegisteredException("Contact value already registered");
    }
    // The admin's login identity exists only once the setup link is used; until then the address
    // is held by the registration token of the signup that sent it.
    if (tenantQueryPort.hasPendingRegistrationFor(contactValue)) {
      throw new SignupAlreadyPendingException(
          "A registration for this contact is already waiting to be completed");
    }
  }
}
