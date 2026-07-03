package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.persistence.TenantSessionBinder;
import com.fabricmanagement.common.infrastructure.tenant.TrialLifecyclePort;
import com.fabricmanagement.common.infrastructure.web.exception.TaxIdAlreadyExistsException;
import com.fabricmanagement.platform.auth.app.onboarding.OnboardingContext;
import com.fabricmanagement.platform.auth.app.onboarding.TenantOnboardingOrchestrator;
import com.fabricmanagement.platform.auth.domain.LoginIdentity;
import com.fabricmanagement.platform.auth.domain.Membership;
import com.fabricmanagement.platform.auth.domain.MembershipStatus;
import com.fabricmanagement.platform.auth.dto.CreateExistingAccountOrganizationRequest;
import com.fabricmanagement.platform.auth.dto.LoginResponse;
import com.fabricmanagement.platform.auth.dto.OnboardingPrefillDto;
import com.fabricmanagement.platform.auth.dto.SignupIntent;
import com.fabricmanagement.platform.auth.dto.TenantOnboardingResponse;
import com.fabricmanagement.platform.auth.infra.repository.LoginIdentityRepository;
import com.fabricmanagement.platform.auth.infra.repository.MembershipRepository;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.organization.api.facade.OrganizationFacade;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Creates a new trial tenant for an already authenticated login identity. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExistingAccountOrganizationService {

  private static final int DEFAULT_SELF_SERVICE_TRIAL_DAYS = 90;

  private final TenantOnboardingOrchestrator orchestrator;
  private final OrganizationFacade organizationFacade;
  private final MembershipRepository membershipRepository;
  private final LoginIdentityRepository loginIdentityRepository;
  private final UserRepository userRepository;
  private final TrialLifecyclePort trialLifecyclePort;
  private final SwitchOrganizationService switchOrganizationService;
  private final TenantSessionBinder tenantSessionBinder;

  @Value("${application.identity.max-organizations:5}")
  private int maxOrganizations;

  @Transactional
  public LoginResponse createOrganization(
      UUID currentUserId,
      CreateExistingAccountOrganizationRequest request,
      String ipAddress,
      String userAgent) {
    Membership currentMembership =
        membershipRepository
            .findByUserId(currentUserId)
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Login membership not found", "AUTH_MEMBERSHIP_NOT_FOUND", 403));

    LoginIdentity identity =
        loginIdentityRepository
            .findById(currentMembership.getLoginIdentityId())
            .filter(loginIdentity -> Boolean.TRUE.equals(loginIdentity.getIsActive()))
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Login identity not found", "AUTH_IDENTITY_NOT_FOUND", 404));

    long activeMembershipCount =
        membershipRepository.countByLoginIdentityIdAndStatus(
            identity.getId(), MembershipStatus.ACTIVE);
    if (activeMembershipCount >= maxOrganizations) {
      throw new PlatformDomainException(
          "Organization limit reached", "AUTH_ORG_LIMIT_REACHED", 400);
    }

    String taxId = trimToNull(request.getTaxId());
    if (taxId != null && organizationFacade.existsByTaxId(taxId)) {
      throw new TaxIdAlreadyExistsException("Organization with this tax ID already exists");
    }

    // /api/v1/auth/* is excluded from the tenant-context interceptor (pre-auth endpoints), so no
    // tenant is bound here and the RLS-scoped User read below would see nothing. Bind the caller's
    // own tenant first — the same bind-before-read pattern SwitchOrganizationService uses. The
    // onboarding pipeline (CreateTenantStep) re-binds to the NEW tenant afterwards.
    TenantContext.setCurrentTenantId(currentMembership.getTenantId());
    tenantSessionBinder.bindToCurrentSession(currentMembership.getTenantId());

    User currentUser =
        userRepository
            .findByTenantIdAndId(currentMembership.getTenantId(), currentUserId)
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Current user not found", "AUTH_USER_NOT_FOUND", 404));

    OnboardingContext context = new OnboardingContext();
    context.setOrganizationName(request.getOrganizationName().trim());
    context.setTaxId(taxId);
    context.setOrganizationType(request.getOrganizationType());
    context.setAdminFirstName(currentUser.getFirstName());
    context.setAdminLastName(currentUser.getLastName());
    context.setAdminContact(identity.getEmail());
    context.setOrganizationEmail(identity.getEmail());
    context.setSelectedOS(resolveSelectedOs(request.getSelectedOS()));
    context.setSignupIntent(SignupIntent.TRIAL.name());
    context.setTrialDays(DEFAULT_SELF_SERVICE_TRIAL_DAYS);
    context.setSalesLed(false);
    context.setDemoMode(false);
    context.setExistingIdentity(true);

    TenantOnboardingResponse onboarding = orchestrator.onboard(context);
    trialLifecyclePort.startSelfServiceTrialIfNeeded(onboarding.getTenantId());

    LoginResponse response =
        switchOrganizationService.switchOrganization(
            currentUserId, onboarding.getTenantId(), ipAddress, userAgent);
    response.setNeedsOnboarding(true);
    response.setOnboardingPrefill(
        OnboardingPrefillDto.builder()
            .primaryEmail(identity.getEmail())
            .organizationName(onboarding.getOrganizationName())
            .legalName(onboarding.getOrganizationName())
            .taxId(taxId)
            .organizationType(request.getOrganizationType().name())
            .build());

    log.info(
        "Existing account organization created: identityId={}, tenantId={}, userId={}",
        identity.getId(),
        onboarding.getTenantId(),
        onboarding.getAdminUserId());
    return response;
  }

  private List<String> resolveSelectedOs(List<String> selectedOs) {
    if (selectedOs == null || selectedOs.isEmpty()) {
      return List.of("FabricOS");
    }
    List<String> sanitized =
        selectedOs.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList();
    return sanitized.isEmpty() ? List.of("FabricOS") : sanitized;
  }

  private String trimToNull(String value) {
    return StringUtils.hasText(value) ? value.trim() : null;
  }
}
