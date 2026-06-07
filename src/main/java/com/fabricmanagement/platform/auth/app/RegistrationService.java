package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.util.DeviceInfoUtil;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import com.fabricmanagement.platform.auth.domain.AuthUser;
import com.fabricmanagement.platform.auth.domain.RefreshToken;
import com.fabricmanagement.platform.auth.domain.VerificationType;
import com.fabricmanagement.platform.auth.domain.event.UserLoginEvent;
import com.fabricmanagement.platform.auth.domain.event.UserRegisteredEvent;
import com.fabricmanagement.platform.auth.dto.LoginResponse;
import com.fabricmanagement.platform.auth.dto.OnboardingPrefillDto;
import com.fabricmanagement.platform.auth.dto.RegisterCheckRequest;
import com.fabricmanagement.platform.auth.dto.VerifyAndRegisterRequest;
import com.fabricmanagement.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.platform.auth.infra.repository.RefreshTokenRepository;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.app.ContactService;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.user.api.facade.UserFacade;
import com.fabricmanagement.platform.user.app.UserContactAssignmentService;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
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
 * Registration Service - User registration with multi-channel verification.
 *
 * <h2>Registration Flow:</h2>
 *
 * <pre>
 * Step 1: Check Eligibility
 *   ├─ Contact MUST exist in User table (pre-approved / invited user)
 *   ├─ User MUST NOT already have AuthUser (not yet registered)
 *   └─ Generate & send verification code
 *
 * Step 2: Verify & Register
 *   ├─ Validate verification code
 *   ├─ Hash password (BCrypt)
 *   ├─ Create AuthUser
 *   ├─ Generate JWT tokens
 *   ├─ Publish UserRegisteredEvent
 *   └─ Return needsOnboarding and onboardingPrefill when applicable
 * </pre>
 *
 * <h2>Multi-Channel Verification:</h2>
 *
 * <p>Priority: WhatsApp → Email → SMS (handled by Communication module)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {

  private final UserFacade userFacade;
  private final UserRepository userRepository;
  private final AuthUserRepository authUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final DomainEventPublisher eventPublisher;
  private final ContactService contactService;
  private final UserContactAssignmentService userContactAssignmentService;
  private final RefreshTokenRepository refreshTokenRepository;
  private final TenantQueryPort tenantQueryPort;
  private final VerificationCodeManager verificationCodeManager;
  private final OrganizationRepository organizationRepository;

  // VerificationService no longer injected - VerificationCodeManager.issueCode sends via dispatcher

  @Value("${application.jwt.expiration:900000}")
  private long accessTokenExpirationMs;

  @Value("${application.jwt.refresh-expiration:604800000}")
  private long refreshTokenExpiration;

  @Transactional
  public String checkEligibilityAndSendCode(RegisterCheckRequest request) {
    log.info(
        "Checking registration eligibility: contactValue={}",
        PiiMaskingUtil.maskEmail(request.getContactValue()));

    UserDto user = userFacade.findByContactValue(request.getContactValue()).orElse(null);

    if (user == null) {
      log.warn(
          "Contact not found in system: {}", PiiMaskingUtil.maskEmail(request.getContactValue()));
      throw new PlatformDomainException(
          "Your information is not registered. Our representative will contact you.",
          "AUTH_NOT_REGISTERED",
          400);
    }

    // Check if user already has AuthUser (user-based authentication)
    if (authUserRepository.existsByUserId(user.getId())) {
      log.warn("User already registered: userId={}", user.getId());
      throw new PlatformDomainException(
          "This account is already registered. Please login.", "AUTH_ALREADY_REGISTERED", 409);
    }

    verificationCodeManager.issueCode(request.getContactValue(), VerificationType.REGISTRATION);

    log.info(
        "Verification code queued for sending (async) to: {}",
        PiiMaskingUtil.maskEmail(request.getContactValue()));

    return "Verification code sent. Please check your email.";
  }

  @Transactional
  public LoginResponse verifyAndRegister(
      VerifyAndRegisterRequest request, String ipAddress, String userAgent) {
    log.info(
        "Verifying and registering: contactValue={}",
        PiiMaskingUtil.maskEmail(request.getContactValue()));

    verificationCodeManager.validateAndConsume(
        request.getContactValue(), VerificationType.REGISTRATION, request.getCode());

    UserDto user =
        userFacade
            .findByContactValue(request.getContactValue())
            .orElseThrow(
                () -> new PlatformDomainException("User not found", "AUTH_USER_NOT_FOUND", 404));

    // TOCTOU guard: re-check AuthUser doesn't exist (may have been created between check & verify)
    if (authUserRepository.existsByUserId(user.getId())) {
      throw new PlatformDomainException(
          "This account is already registered. Please login.", "AUTH_ALREADY_REGISTERED", 409);
    }

    // Tenant status guard: ensure tenant is active before allowing registration
    // Uses TenantQueryPort (BYPASSRLS) — registration happens before tenant context is set
    tenantQueryPort
        .findById(user.getTenantId())
        .orElseThrow(
            () ->
                new PlatformDomainException(
                    "Tenant is not active. Registration is not allowed.",
                    "AUTH_TENANT_INACTIVE",
                    403));

    com.fabricmanagement.platform.communication.domain.Contact contact =
        findUserContactByValue(user.getId(), request.getContactValue());

    contactService.verifyContact(contact.getId());

    String passwordHash = passwordEncoder.encode(request.getPassword());

    // ✅ Create AuthUser for User (user-based authentication)
    // Multi-contact login supported: Any verified contact of this User can be used for login
    AuthUser authUser = AuthUser.create(user.getId(), passwordHash);
    authUser.setTenantId(user.getTenantId());
    authUser.verify();
    authUserRepository.save(authUser);

    com.fabricmanagement.platform.user.domain.User userEntity =
        userRepository
            .findByTenantIdAndId(user.getTenantId(), user.getId())
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "User not found after registration", "AUTH_USER_NOT_FOUND", 404));

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
    refreshTokenEntity.setTenantId(user.getTenantId());
    refreshTokenRepository.save(refreshTokenEntity);

    String contactValue = contact.getContactValue();
    eventPublisher.publish(new UserRegisteredEvent(user.getTenantId(), user.getId(), contactValue));
    // Auto-login event for audit trail (registration is a successful login)
    eventPublisher.publish(
        new UserLoginEvent(user.getTenantId(), user.getId(), contactValue, ipAddress));

    log.info("User registered successfully: userId={}, uid={}", user.getId(), user.getUid());

    boolean needsOnboarding = !Boolean.TRUE.equals(user.getHasCompletedOnboarding());
    OnboardingPrefillDto onboardingPrefill = null;
    if (needsOnboarding) {
      onboardingPrefill = buildOnboardingPrefill(user, contactValue);
    }

    return LoginResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .expiresIn(accessTokenExpirationMs / 1000)
        .user(user)
        .needsOnboarding(needsOnboarding)
        .onboardingPrefill(onboardingPrefill)
        .build();
  }

  private OnboardingPrefillDto buildOnboardingPrefill(UserDto user, String primaryEmail) {
    // Get organization for onboarding prefill (User.organizationId)
    Optional<Organization> orgOpt =
        organizationRepository.findByTenantIdAndId(user.getTenantId(), user.getOrganizationId());
    return OnboardingPrefillDto.builder()
        .primaryEmail(primaryEmail)
        .organizationName(orgOpt.map(Organization::getName).orElse(null))
        .legalName(
            orgOpt
                .map(
                    o -> {
                      String ln = o.getLegalName();
                      return (ln != null && !ln.isBlank()) ? ln : o.getName();
                    })
                .orElse(null))
        .taxId(orgOpt.map(Organization::getTaxId).orElse(null))
        .organizationType(orgOpt.map(o -> o.getOrganizationType().name()).orElse(null))
        .build();
  }

  private com.fabricmanagement.platform.communication.domain.Contact findUserContactByValue(
      UUID userId, String contactValue) {
    // Batch load all contacts for user to avoid N+1 queries
    var userContacts = userContactAssignmentService.getUserContacts(userId);
    var contactIds = userContacts.stream().map(uc -> uc.getContactId()).toList();
    return contactService.findAllById(contactIds).stream()
        .filter(c -> c.getContactValue().equalsIgnoreCase(contactValue))
        .findFirst()
        .or(() -> contactService.findByValue(contactValue))
        .orElseThrow(
            () ->
                new PlatformDomainException(
                    "Contact not found for user", "AUTH_CONTACT_NOT_FOUND", 404));
  }
}
