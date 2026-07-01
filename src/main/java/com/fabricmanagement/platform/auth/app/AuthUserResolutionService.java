package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.platform.auth.config.AuthProperties;
import com.fabricmanagement.platform.auth.domain.AuthUser;
import com.fabricmanagement.platform.auth.domain.LoginIdentity;
import com.fabricmanagement.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.platform.auth.infra.repository.LoginIdentityRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves and validates AuthUser by contact value. Shared by Login, PasswordReset, Registration.
 *
 * <p>Responsibilities: resolveByContact, validate (verified, active, locked), recordFailedAttempt,
 * resetFailedAttempts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthUserResolutionService {

  private final AuthUserRepository authUserRepository;
  private final LoginIdentityRepository loginIdentityRepository;
  private final AuthProperties authProperties;

  private int maxAttempts() {
    return authProperties.getLockout().getMaxAttempts();
  }

  private int lockDurationSeconds() {
    return authProperties.getLockout().getLockDurationSeconds();
  }

  /**
   * Resolve AuthUser by contact value (email or phone).
   *
   * @param contactValue contact value
   * @return AuthUser if found
   */
  @Transactional(readOnly = true)
  public Optional<AuthUser> resolveByContact(String contactValue) {
    return authUserRepository.findByContactValue(contactValue);
  }

  /**
   * Validate AuthUser: verified, active, not locked.
   *
   * @param authUser AuthUser to validate
   * @return validation result
   */
  public AuthValidationResult validate(AuthUser authUser) {
    if (!Boolean.TRUE.equals(authUser.getIsVerified())) {
      return AuthValidationResult.notVerified();
    }
    if (authUser.isLocked()) {
      return AuthValidationResult.locked(authUser.getLockedUntil());
    }
    if (!Boolean.TRUE.equals(authUser.getIsActive())) {
      return AuthValidationResult.inactive();
    }
    return AuthValidationResult.valid();
  }

  /** Validate LoginIdentity: verified, active, not locked, and not collision-reset blocked. */
  public AuthValidationResult validate(LoginIdentity identity) {
    if (!Boolean.TRUE.equals(identity.getEmailVerified())) {
      return AuthValidationResult.notVerified();
    }
    if (identity.isLocked()) {
      return AuthValidationResult.locked(identity.getLockedUntil());
    }
    if (!Boolean.TRUE.equals(identity.getIsActive())) {
      return AuthValidationResult.inactive();
    }
    if (Boolean.TRUE.equals(identity.getRequiresPasswordReset())) {
      return AuthValidationResult.passwordResetRequired();
    }
    return AuthValidationResult.valid();
  }

  /** Record a failed login attempt; lock account if max attempts reached. */
  @Transactional
  public void recordFailedAttempt(AuthUser authUser) {
    authUser.recordFailedLogin(maxAttempts(), lockDurationSeconds());

    if (authUser.isLocked()) {
      log.warn(
          "Account locked: userId={}, attempts={}, until={}",
          authUser.getUserId(),
          authUser.getFailedLoginAttempts(),
          authUser.getLockedUntil());
    }
    authUserRepository.save(authUser);
  }

  /** Record a failed identity login attempt; lock account if max attempts reached. */
  @Transactional
  public void recordFailedAttempt(LoginIdentity identity) {
    identity.recordFailedLogin(maxAttempts(), lockDurationSeconds());

    if (identity.isLocked()) {
      log.warn(
          "LoginIdentity locked: identityId={}, attempts={}, until={}",
          identity.getId(),
          identity.getFailedLoginAttempts(),
          identity.getLockedUntil());
    }
    loginIdentityRepository.save(identity);
  }

  /** Reset failed attempts and set last login. */
  @Transactional
  public void resetFailedAttempts(AuthUser authUser) {
    authUser.recordSuccessfulLogin();
    authUserRepository.save(authUser);
  }

  /** Reset failed identity login attempts after a successful password check. */
  @Transactional
  public void resetFailedAttempts(LoginIdentity identity) {
    identity.recordSuccessfulLogin();
    loginIdentityRepository.save(identity);
  }

  /** Check if user has password (AuthUser exists) by user ID. */
  @Transactional(readOnly = true)
  public boolean existsByUserId(UUID userId) {
    return authUserRepository.existsByUserId(userId);
  }
}
