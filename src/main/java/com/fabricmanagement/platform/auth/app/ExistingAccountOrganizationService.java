package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
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

  @Value("${application.identity.max-organizations:5}")
  private int maxOrganizations;

  // Deliberately NOT @Transactional at this level. One long transaction breaks the flow twice:
  // (1) /api/v1/auth/* skips the tenant-context interceptor, so the connection would be acquired
  // unbound and the RLS-scoped user pre-read sees nothing; (2) the final token issuance reloads the
  // new admin User inside the SAME persistence context that created it, and its contact links
  // (written via separate link entities) are not visible on the cached instance
  // (AUTH_NO_VERIFIED_CONTACT). Instead each part runs its own transaction: the orchestrator is
  // @Transactional (atomic pipeline), and SwitchOrganizationService opens a fresh session that
  // reads the committed user + verified contact from the database.
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

    // Set the caller's tenant on the thread BEFORE the RLS-scoped read: with no surrounding
    // transaction, the read acquires a fresh connection and TenantConnectionProvider binds
    // app.current_tenant from TenantContext at acquisition time.
    TenantContext.setCurrentTenantId(currentMembership.getTenantId());
    try {
      return doCreateOrganization(currentUserId, request, ipAddress, userAgent, identity, taxId);
    } finally {
      TenantContext.clear();
    }
  }

  private LoginResponse doCreateOrganization(
      UUID currentUserId,
      CreateExistingAccountOrganizationRequest request,
      String ipAddress,
      String userAgent,
      LoginIdentity identity,
      String taxId) {
    UUID callerTenantId = TenantContext.requireTenantId();

    User currentUser =
        userRepository
            .findByTenantIdAndId(callerTenantId, currentUserId)
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
