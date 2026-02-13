package com.fabricmanagement.common.platform.auth.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.auth.domain.AuthUser;
import com.fabricmanagement.common.platform.auth.domain.RefreshToken;
import com.fabricmanagement.common.platform.auth.domain.VerificationType;
import com.fabricmanagement.common.platform.auth.domain.event.UserLoginEvent;
import com.fabricmanagement.common.platform.auth.dto.LoginRequest;
import com.fabricmanagement.common.platform.auth.dto.LoginResponse;
import com.fabricmanagement.common.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.common.platform.auth.infra.repository.RefreshTokenRepository;
import com.fabricmanagement.common.platform.communication.app.ContactService;
import com.fabricmanagement.common.platform.organization.api.facade.OrganizationFacade;
import com.fabricmanagement.common.platform.organization.dto.OrganizationDto;
import com.fabricmanagement.common.platform.user.api.facade.UserFacade;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import java.time.Instant;
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

  private final AuthUserRepository authUserRepository;
  private final AuthUserResolutionService resolutionService;
  private final RefreshTokenRepository refreshTokenRepository;
  private final UserFacade userFacade;
  private final UserRepository userRepository;
  private final OrganizationFacade organizationFacade;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final DomainEventPublisher eventPublisher;
  private final ContactService contactService;
  private final VerificationCodeManager verificationCodeManager;

  @Value("${application.jwt.expiration:900000}")
  private long accessTokenExpiration;

  @Value("${application.jwt.refresh-expiration:604800000}")
  private long refreshTokenExpiration;

  @Transactional
  public LoginResponse login(LoginRequest request, String ipAddress) {
    log.info("Login attempt: contactValue={}", PiiMaskingUtil.maskEmail(request.getContactValue()));

    // Debug: Log actual email (only in local/dev profiles)
    if (!PiiMaskingUtil.isMaskingEnabled()) {
      log.debug("Login attempt (unmasked): contactValue={}", request.getContactValue());
    }

    // ✅ Find AuthUser by contact value (user-based authentication)
    Optional<AuthUser> authUserOpt = resolutionService.resolveByContact(request.getContactValue());

    // ✅ If AuthUser doesn't exist, check if contact belongs to user with password
    if (authUserOpt.isEmpty()) {
      log.debug(
          "AuthUser not found for contactValue={}, checking if user exists",
          PiiMaskingUtil.maskEmail(request.getContactValue()));

      Optional<UserDto> userOpt = userFacade.findByContactValue(request.getContactValue());
      if (userOpt.isPresent()) {
        UserDto user = userOpt.get();
        log.debug(
            "User found: userId={}, tenantId={}, checking for password",
            user.getId(),
            user.getTenantId());

        // ✅ Check if user has AuthUser (password exists) - simple user-based check
        boolean userHasPassword = resolutionService.existsByUserId(user.getId());

        log.debug("User has password: {}", userHasPassword);

        if (userHasPassword) {
          // User has password but this contact is not verified
          TenantContext.executeInTenantContext(
              user.getTenantId(),
              () -> checkUnverifiedContact(request.getContactValue()));
        }
      }

      // Contact doesn't exist or user doesn't have password
      log.warn(
          "Login failed: User not found or has no password. contactValue={}",
          PiiMaskingUtil.maskEmail(request.getContactValue()));
      throw createContextAwareNotFoundException(request.getContactValue());
    }

    AuthUser authUser = authUserOpt.get();

    AuthValidationResult validation = resolutionService.validate(authUser);
    if (!validation.isValid()) {
      log.warn(
          "Auth validation failed: contactValue={}, reason={}",
          PiiMaskingUtil.maskEmail(request.getContactValue()),
          validation.getReason());
      throw new IllegalArgumentException(validation.getReason());
    }

    if (!passwordEncoder.matches(request.getPassword(), authUser.getPasswordHash())) {
      resolutionService.recordFailedAttempt(authUser);
      log.warn(
          "Invalid password: contactValue={}, attempts={}",
          PiiMaskingUtil.maskEmail(request.getContactValue()),
          authUser.getFailedLoginAttempts());
      throw new IllegalArgumentException("Invalid credentials");
    }

    resolutionService.resetFailedAttempts(authUser);

    // ✅ Get User from AuthUser (user-based authentication)
    // AuthUser.user is already loaded via LEFT JOIN FETCH in repository query
    UUID userId = authUser.getUserId();
    UserDto user =
        userFacade
            .findById(authUser.getTenantId(), userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    if (!user.getIsActive()) {
      throw new IllegalArgumentException("User account is deactivated");
    }

    // Get User entity with contacts/departments loaded for JWT generation
    com.fabricmanagement.common.platform.user.domain.User userEntity =
        userRepository
            .findByTenantIdAndId(user.getTenantId(), user.getId())
            .orElseThrow(() -> new IllegalArgumentException("User entity not found"));

    String accessToken = jwtService.generateAccessToken(userEntity);
    String refreshToken = jwtService.generateRefreshToken(userEntity);

    RefreshToken refreshTokenEntity =
        RefreshToken.create(
            user.getId(), refreshToken, Instant.now().plusMillis(refreshTokenExpiration));
    refreshTokenRepository.save(refreshTokenEntity);

    // Get contact value from Contact entity
    String contactValue =
        userEntity
            .getAnyVerifiedContact()
            .map(contact -> contact.getContactValue())
            .orElse(request.getContactValue());

    eventPublisher.publish(
        new UserLoginEvent(user.getTenantId(), user.getId(), contactValue, ipAddress));

    log.info("Login successful: userId={}, uid={}", user.getId(), user.getUid());

    return LoginResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .expiresIn(accessTokenExpiration / 1000)
        .user(user)
        .needsOnboarding(!Boolean.TRUE.equals(user.getHasCompletedOnboarding()))
        .build();
  }

  /**
   * Create context-aware user not found exception.
   *
   * <p>Provides helpful error messages based on context:
   *
   * <ul>
   *   <li>Known tenant company user: "Contact your IT team"
   *   <li>Known supplier company user: "Contact your customer representative"
   *   <li>Unknown: "Sign up at fabricmanagement.com"
   * </ul>
   */
  /**
   * Create context-aware "user not found" exception.
   *
   * <p>Attempts to provide helpful error messages based on domain analysis:
   *
   * <ul>
   *   <li>If domain exists in system: Try to find company and provide company-specific message
   *   <li>Otherwise: Generic signup message
   * </ul>
   *
   * <p><b>Note:</b> This method does NOT create incorrect email patterns. It only checks if the
   * domain exists in the system to provide better context.
   */
  private IllegalArgumentException createContextAwareNotFoundException(String contactValue) {
    String domain = extractEmailDomain(contactValue);
    if (domain == null) {
      return new IllegalArgumentException("User not found. Please check your credentials.");
    }

    // Check if any contact exists with this domain (for context-aware error messages)
    boolean domainExists = contactService.existsByEmailDomain(domain);

    if (domainExists) {
      // Domain exists in system - try to find a user with this domain to get company context
      // We'll search for any user contact with this domain pattern
      // Note: We don't create incorrect emails, we just check domain existence

      // Try to find any user with contacts in this domain
      // This helps provide better error messages without creating false email patterns
      Optional<UserDto> anyUserWithDomain = findAnyUserWithDomain(domain);

      if (anyUserWithDomain.isPresent()) {
        UserDto existingUser = anyUserWithDomain.get();
        Optional<OrganizationDto> organization =
            organizationFacade.findById(
                existingUser.getTenantId(), existingUser.getOrganizationId());

        if (organization.isPresent()) {
          OrganizationDto orgDto = organization.get();

          // Root organization (no parent) is the tenant's main organization
          if (orgDto.getParentOrganizationId() == null) {
            return new IllegalArgumentException(
                "User not found. If you're a "
                    + orgDto.getName()
                    + " employee, please contact your IT team or manager to add you to the system.");
          } else {
            return new IllegalArgumentException(
                "User not found. Please contact your customer representative at "
                    + orgDto.getName()
                    + " to add you to the system.");
          }
        }
      }
    }

    return new IllegalArgumentException(
        "User not found. If you're a new customer, please sign up at fabricmanagement.com");
  }

  /**
   * Find any user with contacts in the given domain. Uses domain pattern matching without creating
   * incorrect email addresses.
   *
   * <p><b>Note:</b> This method does NOT create incorrect email patterns. It uses LIKE query with
   * domain pattern to find users with contacts in this domain.
   *
   * @param domain Email domain (e.g., "gmail.com")
   * @return Optional user if found (converted to UserDto)
   */
  private Optional<UserDto> findAnyUserWithDomain(String domain) {
    if (domain == null || domain.isBlank()) {
      return Optional.empty();
    }

    String normalizedDomain = domain.trim().toLowerCase();
    log.trace("Finding any user with domain: {}", normalizedDomain);

    return userRepository.findAnyByEmailDomain(normalizedDomain).map(UserDto::from);
  }

  /**
   * Extract domain from email address.
   *
   * @param email Email address
   * @return Domain part (e.g., "gmail.com") or null if invalid
   */
  private String extractEmailDomain(String email) {
    if (email == null || !email.contains("@")) {
      return null;
    }
    String[] parts = email.split("@");
    if (parts.length != 2 || parts[1].isBlank()) {
      return null;
    }
    return parts[1].trim().toLowerCase();
  }

  /**
   * Check if contact is unverified and send verification code if so.
   *
   * <p>Must be called within the correct TenantContext.
   *
   * @param contactValue Contact value to check
   * @throws IllegalArgumentException if contact is not verified
   */
  private void checkUnverifiedContact(String contactValue) {
    com.fabricmanagement.common.platform.communication.domain.Contact contact =
        contactService.findByValue(contactValue).orElse(null);

    if (contact != null && !Boolean.TRUE.equals(contact.getIsVerified())) {
      log.info(
          "Contact not verified, sending verification code: contactValue={}",
          PiiMaskingUtil.maskEmail(contactValue));

      VerificationType verificationType =
          contact.getContactType() != null && contact.getContactType().isMobile()
              ? VerificationType.PHONE_VERIFICATION
              : VerificationType.EMAIL_VERIFICATION;

      try {
        verificationCodeManager.issueCode(contactValue, verificationType);
      } catch (IllegalArgumentException | IllegalStateException ex) {
        log.warn(
            "Verification code issuance failed: contactValue={}, reason={}",
            PiiMaskingUtil.maskEmail(contactValue),
            ex.getMessage());
        throw new IllegalArgumentException("Contact not verified. " + ex.getMessage());
      }

      throw new IllegalArgumentException(
          "Contact not verified. Verification code sent to "
              + PiiMaskingUtil.maskEmail(contactValue)
              + ". Please verify your contact first.");
    }
  }
}
