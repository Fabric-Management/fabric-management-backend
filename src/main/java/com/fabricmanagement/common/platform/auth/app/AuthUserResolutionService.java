package com.fabricmanagement.common.platform.auth.app;

import com.fabricmanagement.common.platform.auth.config.AuthProperties;
import com.fabricmanagement.common.platform.auth.domain.AuthUser;
import com.fabricmanagement.common.platform.auth.infra.repository.AuthUserRepository;
import java.time.Instant;
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

  private static final int DEFAULT_MAX_ATTEMPTS = 5;
  private static final int DEFAULT_LOCK_SECONDS = 1800; // 30 minutes

  private final AuthUserRepository authUserRepository;
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

  /** Record a failed login attempt; lock account if max attempts reached. */
  @Transactional
  public void recordFailedAttempt(AuthUser authUser) {
    int attempts =
        (authUser.getFailedLoginAttempts() != null ? authUser.getFailedLoginAttempts() : 0) + 1;
    authUser.setFailedLoginAttempts(attempts);
    if (attempts >= maxAttempts()) {
      authUser.setLockedUntil(Instant.now().plusSeconds(lockDurationSeconds()));
      log.warn(
          "Account locked: userId={}, attempts={}, until={}",
          authUser.getUserId(),
          attempts,
          authUser.getLockedUntil());
    }
    authUserRepository.save(authUser);
  }

  /** Reset failed attempts and set last login. */
  @Transactional
  public void resetFailedAttempts(AuthUser authUser) {
    authUser.recordSuccessfulLogin();
    authUserRepository.save(authUser);
  }

  /** Check if user has password (AuthUser exists) by user ID. */
  @Transactional(readOnly = true)
  public boolean existsByUserId(UUID userId) {
    return authUserRepository.existsByUserId(userId);
  }
}
