package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.persistence.TenantSessionBinder;
import com.fabricmanagement.common.util.DeviceInfoUtil;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import com.fabricmanagement.platform.auth.domain.LoginIdentity;
import com.fabricmanagement.platform.auth.domain.Membership;
import com.fabricmanagement.platform.auth.domain.MembershipStatus;
import com.fabricmanagement.platform.auth.domain.MfaType;
import com.fabricmanagement.platform.auth.domain.RefreshToken;
import com.fabricmanagement.platform.auth.domain.event.UserLoginEvent;
import com.fabricmanagement.platform.auth.dto.LoginRequest;
import com.fabricmanagement.platform.auth.dto.LoginResponse;
import com.fabricmanagement.platform.auth.dto.VerifyMfaRequest;
import com.fabricmanagement.platform.auth.infra.repository.LoginIdentityRepository;
import com.fabricmanagement.platform.auth.infra.repository.MembershipRepository;
import com.fabricmanagement.platform.auth.infra.repository.RefreshTokenRepository;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.user.api.facade.UserFacade;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login Service - Authentication logic.
 *
 * <h2>Flow:</h2>
 *
 * <ol>
 *   <li>Validate credentials (contact + password)
 *   <li>Check user status (verified, active, not locked)
 *   <li>Generate tokens (access + refresh)
 *   <li>Publish login event
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginService {

  private final AuthUserResolutionService resolutionService;
  private final LoginIdentityRepository loginIdentityRepository;
  private final MembershipRepository membershipRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final UserFacade userFacade;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final DomainEventPublisher eventPublisher;
  private final VerificationCodeManager verificationCodeManager;
  private final TotpMfaService totpMfaService;
  private final TrustedDeviceService trustedDeviceService;
  private final MfaRateLimitService mfaRateLimitService;
  private final MfaEventService mfaEventService;
  private final TenantSessionBinder tenantSessionBinder;

  @Value("${application.jwt.expiration:900000}")
  private long accessTokenExpiration;

  @Value("${application.jwt.refresh-expiration:604800000}")
  private long refreshTokenExpiration;

  /** Hash of a random value, created on first use; see {@link #spendPasswordCheck(String)}. */
  private volatile String timingPlaceholderHash;

  @Transactional
  public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {
    log.info("Login attempt: contactValue={}", PiiMaskingUtil.maskEmail(request.getContactValue()));

    // Debug: Log actual email (only in local/dev profiles)
    if (!PiiMaskingUtil.isMaskingEnabled()) {
      log.debug("Login attempt (unmasked): contactValue={}", request.getContactValue());
    }

    String normalizedEmail = normalizeEmail(request.getContactValue());
    Optional<LoginIdentity> identityOpt = loginIdentityRepository.findByEmail(normalizedEmail);

    if (identityOpt.isEmpty()) {
      spendPasswordCheck(request.getPassword());
      log.warn(
          "Login failed: no login identity. contactValue={}",
          PiiMaskingUtil.maskEmail(request.getContactValue()));
      throw invalidCredentials();
    }

    LoginIdentity identity = identityOpt.get();

    // A locked account answers exactly like a wrong password. Revealing the lock would confirm
    // that the account exists, and checking the password during the lock would let a caller keep
    // guessing and learn when a guess is right.
    if (identity.isLocked()) {
      spendPasswordCheck(request.getPassword());
      log.warn(
          "Login rejected: identity locked. identityId={}, lockedUntil={}",
          identity.getId(),
          identity.getLockedUntil());
      throw invalidCredentials();
    }

    if (!passwordMatches(request.getPassword(), identity.getPasswordHash())) {
      resolutionService.recordFailedAttempt(identity);
      log.warn(
          "Invalid password: contactValue={}, attempts={}",
          PiiMaskingUtil.maskEmail(request.getContactValue()),
          identity.getFailedLoginAttempts());
      throw invalidCredentials();
    }

    // Account status is disclosed only to a caller who has proven the password.
    AuthValidationResult validation = resolutionService.validate(identity);
    if (!validation.isValid()) {
      log.warn(
          "Auth validation failed: contactValue={}, code={}",
          PiiMaskingUtil.maskEmail(request.getContactValue()),
          validation.getErrorCode());
      throw new PlatformDomainException(
          validation.getReason(), validation.getErrorCode(), validation.getHttpStatus());
    }

    resolutionService.resetFailedAttempts(identity);

    Membership membership = selectLoginMembership(identity);
    UUID tenantId = membership.getTenantId();
    UUID userId = membership.getUserId();

    TenantContext.setCurrentTenantId(tenantId);
    tenantSessionBinder.bindToCurrentSession(tenantId);

    try {
      UserDto user = userFacade.findById(tenantId, userId).orElseThrow();

      if (!user.getIsActive()) {
        throw new PlatformDomainException(
            "User account is deactivated", "AUTH_USER_DEACTIVATED", 403);
      }

      // Get User entity with contacts/departments loaded for JWT generation
      com.fabricmanagement.platform.user.domain.User userEntity =
          userRepository
              .findByTenantIdAndId(tenantId, userId)
              .orElseThrow(() -> new IllegalArgumentException("User entity not found"));

      // Check if MFA is enabled and handle it
      boolean bypassMfa = false;

      // Check trusted device token with full context binding (IP + User-Agent)
      if (request.getTrustedDeviceToken() != null && !request.getTrustedDeviceToken().isBlank()) {
        bypassMfa =
            trustedDeviceService.validateDevice(
                request.getTrustedDeviceToken(), user.getId(), ipAddress, userAgent);
        if (bypassMfa) {
          log.info(
              "MFA bypassed due to valid trusted device token for user: {}",
              PiiMaskingUtil.maskEmail(request.getContactValue()));
        } else {
          log.warn(
              "Invalid or expired trusted device token for user: {}",
              PiiMaskingUtil.maskEmail(request.getContactValue()));
        }
      }

      if (!bypassMfa && Boolean.TRUE.equals(identity.getIsMfaEnabled())) {
        MfaType mfaType =
            identity.getPrimaryMfaType() != null ? identity.getPrimaryMfaType() : MfaType.NONE;

        if (mfaType != MfaType.NONE) {
          String preAuthToken = jwtService.generatePreAuthToken(userEntity);

          String maskedContact = null;
          if (mfaType == MfaType.EMAIL || mfaType == MfaType.SMS || mfaType == MfaType.WHATSAPP) {
            VerificationCodeManager.MfaCodeIssuanceResult mfaResult =
                verificationCodeManager.issueMfaCode(user.getId(), user.getTenantId(), mfaType);
            maskedContact = mfaResult.maskedContact();
          }

          log.info(
              "MFA required for user: {}, mfaType={}",
              PiiMaskingUtil.maskEmail(request.getContactValue()),
              mfaType);
          return LoginResponse.builder()
              .mfaRequired(true)
              .mfaToken(preAuthToken)
              .mfaType(mfaType)
              .maskedContact(maskedContact)
              .build();
        }
      }

      String accessToken = jwtService.generateAccessToken(userEntity);
      String refreshToken = jwtService.generateRefreshToken(userEntity);

      RefreshToken refreshTokenEntity =
          RefreshToken.create(
              user.getId(),
              refreshToken,
              Instant.now().plusMillis(refreshTokenExpiration),
              ipAddress,
              userAgent,
              DeviceInfoUtil.extractDeviceName(userAgent));
      TenantContext.executeInTenantContext(
          user.getTenantId(), () -> refreshTokenRepository.save(refreshTokenEntity));

      // Get contact value from Contact entity
      String contactValue =
          userEntity
              .getAnyVerifiedContact()
              .map(contact -> contact.getContactValue())
              .orElse(request.getContactValue());

      eventPublisher.publish(
          new UserLoginEvent(user.getTenantId(), user.getId(), contactValue, ipAddress));

      log.info(
          "Login successful: contactValue={}, userId={}, uid={}",
          PiiMaskingUtil.maskEmail(request.getContactValue()),
          user.getId(),
          user.getUid());

      return LoginResponse.builder()
          .accessToken(accessToken)
          .refreshToken(refreshToken)
          .expiresIn(accessTokenExpiration / 1000)
          .user(user)
          .needsOnboarding(!Boolean.TRUE.equals(user.getHasCompletedOnboarding()))
          .build();
    } finally {
      TenantContext.clear();
    }
  }

  @Transactional
  public LoginResponse verifyMfa(VerifyMfaRequest request, String ipAddress, String userAgent) {
    if (!jwtService.validateToken(request.getMfaToken())) {
      throw new PlatformDomainException(
          "Invalid or expired MFA token", "AUTH_MFA_TOKEN_INVALID", 401);
    }

    if (!jwtService.isPreAuthToken(request.getMfaToken())) {
      throw new PlatformDomainException(
          "Provided token is not a valid MFA temporary token", "AUTH_MFA_TOKEN_INVALID", 401);
    }

    UUID userId = jwtService.getUserIdFromToken(request.getMfaToken());
    UUID tenantId = jwtService.getTenantIdFromToken(request.getMfaToken());

    // Brute-force protection: reject if user exceeded max failed MFA attempts
    mfaRateLimitService.checkRateLimit(userId);

    Membership membership =
        membershipRepository
            .findByUserId(userId)
            .filter(
                m -> m.getTenantId().equals(tenantId) && m.getStatus() == MembershipStatus.ACTIVE)
            .orElseThrow(() -> new IllegalArgumentException("Login membership not found"));
    LoginIdentity identity =
        loginIdentityRepository
            .findById(membership.getLoginIdentityId())
            .orElseThrow(() -> new IllegalArgumentException("Login identity not found"));

    TenantContext.setCurrentTenantId(tenantId);
    tenantSessionBinder.bindToCurrentSession(tenantId);

    try {
      com.fabricmanagement.platform.user.domain.User userEntity =
          userRepository
              .findByTenantIdAndId(tenantId, userId)
              .orElseThrow(() -> new IllegalArgumentException("User entity not found"));

      MfaType mfaType = identity.getPrimaryMfaType();

      try {
        if (mfaType == MfaType.TOTP) {
          boolean isValid = totpMfaService.verifyCode(identity.getMfaSecret(), request.getCode());
          if (!isValid) {
            throw new PlatformDomainException("Invalid TOTP code", "AUTH_MFA_INVALID_CODE", 400);
          }
        } else if (mfaType == MfaType.EMAIL
            || mfaType == MfaType.SMS
            || mfaType == MfaType.WHATSAPP) {
          verificationCodeManager.validateMfaCode(userId, tenantId, mfaType, request.getCode());
        } else {
          throw new PlatformDomainException(
              "MFA is not enabled or type is invalid", "AUTH_MFA_NOT_ENABLED", 400);
        }
      } catch (IllegalArgumentException e) {
        mfaRateLimitService.recordFailedAttempt(userId);
        throw e;
      }

      mfaRateLimitService.clearAttempts(userId);
      mfaEventService.pushCompletionEvent(userId);

      // Success! Generate actual tokens
      String accessToken = jwtService.generateAccessToken(userEntity);
      String refreshToken = jwtService.generateRefreshToken(userEntity);

      RefreshToken refreshTokenEntity =
          RefreshToken.create(
              userId,
              refreshToken,
              Instant.now().plusMillis(refreshTokenExpiration),
              ipAddress,
              userAgent,
              DeviceInfoUtil.extractDeviceName(userAgent));
      TenantContext.executeInTenantContext(
          tenantId, () -> refreshTokenRepository.save(refreshTokenEntity));

      String newTrustedDeviceToken = null;
      if (Boolean.TRUE.equals(request.getRememberDevice())) {
        newTrustedDeviceToken =
            trustedDeviceService.createTrustedDevice(userId, ipAddress, userAgent);
      }

      UserDto userDto = userFacade.findById(tenantId, userId).orElseThrow();

      String contactValue = jwtService.getContactValueFromToken(request.getMfaToken());

      eventPublisher.publish(new UserLoginEvent(tenantId, userId, contactValue, ipAddress));

      return LoginResponse.builder()
          .accessToken(accessToken)
          .refreshToken(refreshToken)
          .expiresIn(accessTokenExpiration / 1000)
          .user(userDto)
          .mfaRequired(false)
          .trustedDeviceToken(newTrustedDeviceToken)
          .needsOnboarding(!Boolean.TRUE.equals(userDto.getHasCompletedOnboarding()))
          .build();
    } finally {
      TenantContext.clear();
    }
  }

  private Membership selectLoginMembership(LoginIdentity identity) {
    List<Membership> activeMemberships =
        membershipRepository.findByLoginIdentityIdAndStatus(
            identity.getId(), MembershipStatus.ACTIVE);

    if (activeMemberships.isEmpty()) {
      throw new PlatformDomainException(
          "No active organization membership found", "AUTH_NO_ACTIVE_MEMBERSHIP", 403);
    }

    if (activeMemberships.size() == 1) {
      return activeMemberships.getFirst();
    }

    return activeMemberships.stream()
        .filter(membership -> Boolean.TRUE.equals(membership.getIsDefault()))
        .min(
            Comparator.comparing(
                Membership::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
        .orElseGet(
            () -> {
              log.warn(
                  "LoginIdentity {} has {} active memberships but no default; using first membership.",
                  identity.getId(),
                  activeMemberships.size());
              return activeMemberships.getFirst();
            });
  }

  private String normalizeEmail(String contactValue) {
    if (contactValue == null || contactValue.isBlank()) {
      throw invalidCredentials();
    }
    return contactValue.trim().toLowerCase(Locale.ROOT);
  }

  private static PlatformDomainException invalidCredentials() {
    return new PlatformDomainException("Invalid credentials", "AUTH_INVALID_CREDENTIALS", 401);
  }

  private boolean passwordMatches(String rawPassword, String passwordHash) {
    if (passwordHash == null || passwordHash.isBlank()) {
      spendPasswordCheck(rawPassword);
      return false;
    }
    return passwordEncoder.matches(rawPassword == null ? "" : rawPassword, passwordHash);
  }

  /**
   * Runs one password-hash comparison and discards the result, so a request for a missing or locked
   * account costs about as much as a wrong password. This narrows the response-time difference
   * between those cases; it does not guarantee to remove it.
   */
  private void spendPasswordCheck(String rawPassword) {
    String placeholder = timingPlaceholderHash;
    if (placeholder == null) {
      placeholder = passwordEncoder.encode(UUID.randomUUID().toString());
      timingPlaceholderHash = placeholder;
    }
    if (placeholder != null) {
      passwordEncoder.matches(rawPassword == null ? "" : rawPassword, placeholder);
    }
  }
}
