package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.persistence.TenantSessionBinder;
import com.fabricmanagement.common.infrastructure.tenant.TrialLifecyclePort;
import com.fabricmanagement.common.infrastructure.web.exception.TaxIdAlreadyExistsException;
import com.fabricmanagement.platform.auth.app.AccountSetupService.SetupTarget;
import com.fabricmanagement.platform.auth.domain.PendingSignup;
import com.fabricmanagement.platform.auth.dto.LoginResponse;
import com.fabricmanagement.platform.auth.dto.SetupLinkStatus;
import com.fabricmanagement.platform.auth.dto.TenantOnboardingResponse;
import com.fabricmanagement.platform.auth.infra.repository.PendingSignupRepository;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.organization.api.facade.OrganizationFacade;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Completes a self-service signup when its e-mailed link is used (SIGNUP-DEFER-1 / TD-18).
 *
 * <p>This is the moment the e-mail address is proved, and the only moment the company is built:
 * tenant, organisation, admin user, subscriptions, demo data (the onboarding steps), then the
 * admin's login. It is <b>one transaction</b>. If any step fails nothing is left behind and the
 * pending signup is still usable until its link expires.
 *
 * <p>The pending row is locked while this runs, so a double click on the link cannot build two
 * companies; the second request waits, then finds the signup completed and is refused like any used
 * link.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignupCompletionService {

  private static final String TAX_ID_TAKEN = "Organization with this tax ID already exists";

  private final PendingSignupRepository pendingSignups;
  private final TenantOnboardingService onboarding;
  private final AccountSetupService accountSetup;
  private final IdentityProvisioningService identities;
  private final OrganizationFacade organizationFacade;
  private final TrialLifecyclePort trialLifecyclePort;
  private final TenantSessionBinder tenantSessionBinder;

  /**
   * @return the sign-in response when the token belongs to a pending signup; empty when it does not
   *     (the caller then tries the registration-token links of sales-led and invited users)
   * @throws PlatformDomainException {@code AUTH_TOKEN_INVALID} when the signup is expired or
   *     already completed
   */
  @Transactional
  public Optional<LoginResponse> complete(String rawToken, String rawPassword, String ipAddress) {
    PendingSignup pending =
        pendingSignups.findLockedByTokenHash(SignupLinkToken.hash(rawToken)).orElse(null);
    if (pending == null) {
      return Optional.empty();
    }

    Instant now = Instant.now();
    if (pending.getCompletedAt() != null || !pending.getExpiresAt().isAfter(now)) {
      log.warn(
          "Signup completion rejected: pendingSignupId={}, reason=AUTH_TOKEN_INVALID",
          pending.getId());
      throw invalidToken();
    }

    // Anonymous request: the company is built the way a public signup always was, from a session
    // bound to the platform tenant. The tenant step re-binds the session to the new tenant.
    TenantContext.setCurrentTenantId(TenantContext.SYSTEM_TENANT_ID);
    tenantSessionBinder.bindToCurrentSession(TenantContext.SYSTEM_TENANT_ID);

    // The number was free when the form was sent; sales staff may have registered it since.
    if (organizationFacade.existsByTaxId(pending.getTaxId())) {
      log.warn(
          "Signup completion refused: tax id taken since, pendingSignupId={}", pending.getId());
      throw new TaxIdAlreadyExistsException(TAX_ID_TAKEN);
    }

    TenantOnboardingResponse tenant = onboarding.createVerifiedSelfServiceTenant(pending);

    // The tenant row is not committed yet: the trial starts on this transaction's connection,
    // which the tenant table's self-row policy only lets through when bound to the new tenant.
    TenantContext.setCurrentTenantId(tenant.getTenantId());
    tenantSessionBinder.bindToCurrentSession(tenant.getTenantId());
    if (trialLifecyclePort.startSelfServiceTrialIfNeeded(tenant.getTenantId()) == 0) {
      log.debug(
          "No trial started at signup completion (demo or already started): tenantId={}",
          tenant.getTenantId());
    }

    // Flushes the tenant first, then clears the persistence context: the admin user is reloaded
    // from the database below, with the contacts the onboarding steps wrote.
    pendingSignups.markCompleted(pending.getId(), tenant.getTenantId(), now);

    LoginResponse response =
        accountSetup.finishSetup(
            new SetupTarget(
                tenant.getTenantId(),
                tenant.getAdminUserId(),
                tenant.getOrganizationId(),
                pending.getEmail(),
                true),
            rawPassword,
            ipAddress);
    log.info(
        "Signup completed: pendingSignupId={}, tenantId={}", pending.getId(), tenant.getTenantId());
    return Optional.of(response);
  }

  /**
   * Whether a signup link can still be used, and whether its page must ask for a password. Does not
   * use the link.
   *
   * @return empty when the token belongs to no pending signup
   */
  @Transactional(readOnly = true)
  public Optional<SetupLinkStatus> checkLink(String rawToken) {
    return pendingSignups
        .findByTokenHash(SignupLinkToken.hash(rawToken))
        .map(
            pending -> {
              boolean usable =
                  pending.getCompletedAt() == null && pending.getExpiresAt().isAfter(Instant.now());
              return usable
                  ? new SetupLinkStatus(true, !identities.isEmailRegistered(pending.getEmail()))
                  : SetupLinkStatus.invalid();
            });
  }

  static PlatformDomainException invalidToken() {
    return new PlatformDomainException(
        "Registration token is invalid or expired", "AUTH_TOKEN_INVALID", 400);
  }
}
