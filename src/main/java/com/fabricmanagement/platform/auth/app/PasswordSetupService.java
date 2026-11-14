package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.persistence.TenantSessionBinder;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.infrastructure.tenant.TenantReference;
import com.fabricmanagement.common.infrastructure.tenant.TrialLifecyclePort;
import com.fabricmanagement.platform.auth.app.AccountSetupService.SetupTarget;
import com.fabricmanagement.platform.auth.domain.RegistrationToken;
import com.fabricmanagement.platform.auth.domain.RegistrationTokenType;
import com.fabricmanagement.platform.auth.dto.LoginResponse;
import com.fabricmanagement.platform.auth.dto.PasswordSetupRequest;
import com.fabricmanagement.platform.auth.dto.SetupLinkStatus;
import com.fabricmanagement.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.platform.auth.infra.repository.RegistrationTokenRepository;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.user.api.facade.UserFacade;
import com.fabricmanagement.platform.user.dto.UserDto;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Setup links: the page behind {@code /setup?token=...}.
 *
 * <h2>Two kinds of link</h2>
 *
 * <ul>
 *   <li><b>Self-service signup:</b> the token belongs to a pending signup. Using it proves the
 *       e-mail address and builds the company ({@link SignupCompletionService}). Tried first.
 *   <li><b>Sales-led and invited users:</b> the token is a {@link RegistrationToken} of a user that
 *       already exists. Using it gives that user their login.
 * </ul>
 *
 * <p>Both end the same way ({@link AccountSetupService}): the login is created and the person is
 * signed in, unless the address already had a login, in which case the company is added to it and
 * the person signs in normally.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordSetupService {

  private final RegistrationTokenRepository tokenRepository;
  private final AuthUserRepository authUserRepository;
  private final UserFacade userFacade;
  private final TenantQueryPort tenantQueryPort;
  private final TrialLifecyclePort trialLifecyclePort;
  private final TenantSessionBinder tenantSessionBinder;
  private final IdentityProvisioningService identityProvisioningService;
  private final SignupCompletionService signupCompletion;
  private final AccountSetupService accountSetup;

  /**
   * Complete a setup link: the e-mail address is proved by the click, the password is the person's
   * choice (not needed when the address already has a login).
   *
   * @param request Password setup request
   * @return Login response with tokens, onboarding status and onboardingPrefill when needed; or
   *     {@code signInRequired} when the company was added to an existing login
   */
  @Transactional
  public LoginResponse setupPassword(PasswordSetupRequest request, String ipAddress) {
    log.info("Password setup initiated");

    // A link from a self-service signup form: the company does not exist yet.
    Optional<LoginResponse> completedSignup =
        signupCompletion.complete(request.getToken(), request.getPassword(), ipAddress);
    if (completedSignup.isPresent()) {
      return completedSignup.get();
    }

    // RLS: common_registration_token is tenant-scoped, but password setup is anonymous (no tenant
    // context yet), so the RLS-bound reads below can't see the token. Resolve the token's tenant
    // via
    // the BYPASSRLS query port and bind the DB session, so the token/user/contact reads that follow
    // run under the correct tenant. See TenantQueryPort / TenantSessionBinder.
    UUID tokenTenantId =
        tenantQueryPort
            .findTenantIdByRegistrationToken(request.getToken())
            .orElseThrow(SignupCompletionService::invalidToken);
    TenantContext.setCurrentTenantId(tokenTenantId);
    tenantSessionBinder.bindToCurrentSession(tokenTenantId);

    RegistrationToken token =
        tokenRepository
            .findByToken(request.getToken())
            .orElseThrow(SignupCompletionService::invalidToken);

    if (!token.isValid()) {
      log.warn("Password setup rejected: tenantId={}, reason=AUTH_TOKEN_INVALID", tokenTenantId);
      throw SignupCompletionService.invalidToken();
    }

    log.debug("Password setup tenant resolved: tenantId={}", tokenTenantId);

    UserDto user =
        userFacade
            .findByContactValue(token.getContactValue())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    // ✅ Check if user already has AuthUser (user-based authentication)
    if (authUserRepository.existsByUserId(user.getId())) {
      throw new PlatformDomainException(
          "User already has password set", "AUTH_PASSWORD_ALREADY_SET", 409);
    }

    // Tenant status guard: ensure tenant is active before allowing registration
    // Uses TenantQueryPort (BYPASSRLS) — password setup happens before tenant context is set
    TenantReference tenantRef =
        tenantQueryPort
            .findById(user.getTenantId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Tenant not found or not active. Password setup is not allowed."));
    if (tenantRef != null && token.getTokenType() == RegistrationTokenType.SELF_SERVICE) {
      trialLifecyclePort.startSelfServiceTrialIfNeeded(user.getTenantId());
    }

    token.markAsUsed();
    tokenRepository.save(token);

    return accountSetup.finishSetup(
        new SetupTarget(
            user.getTenantId(),
            user.getId(),
            user.getOrganizationId(),
            token.getContactValue(),
            !Boolean.TRUE.equals(user.getHasCompletedOnboarding())),
        request.getPassword(),
        ipAddress);
  }

  /**
   * Tells the setup page whether a link can still be used and whether it must ask for a password,
   * without using it. Every unusable link (unknown, expired, used) gets the same answer.
   */
  @Transactional(readOnly = true)
  public SetupLinkStatus checkLink(String rawToken) {
    Optional<SetupLinkStatus> signupLink = signupCompletion.checkLink(rawToken);
    if (signupLink.isPresent()) {
      return signupLink.get();
    }

    Optional<UUID> tokenTenantId = tenantQueryPort.findTenantIdByRegistrationToken(rawToken);
    if (tokenTenantId.isEmpty()) {
      return SetupLinkStatus.invalid();
    }
    TenantContext.setCurrentTenantId(tokenTenantId.get());
    tenantSessionBinder.bindToCurrentSession(tokenTenantId.get());

    return tokenRepository
        .findByToken(rawToken)
        .filter(RegistrationToken::isValid)
        // Same answer as setupPassword, which refuses a link whose user already has a password.
        .filter(token -> !userAlreadyHasPassword(token.getContactValue()))
        .map(
            token ->
                new SetupLinkStatus(
                    true, !identityProvisioningService.isEmailRegistered(token.getContactValue())))
        .orElseGet(SetupLinkStatus::invalid);
  }

  private boolean userAlreadyHasPassword(String contactValue) {
    return userFacade
        .findByContactValue(contactValue)
        .map(user -> authUserRepository.existsByUserId(user.getId()))
        .orElse(false);
  }
}
