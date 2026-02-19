package com.fabricmanagement.common.platform.auth.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.platform.auth.domain.AuthUser;
import com.fabricmanagement.common.platform.auth.domain.RefreshToken;
import com.fabricmanagement.common.platform.auth.domain.VerificationType;
import com.fabricmanagement.common.platform.auth.domain.event.UserLoginEvent;
import com.fabricmanagement.common.platform.auth.domain.event.UserRegisteredEvent;
import com.fabricmanagement.common.platform.auth.dto.LoginResponse;
import com.fabricmanagement.common.platform.auth.dto.OnboardingPrefillDto;
import com.fabricmanagement.common.platform.auth.dto.RegisterCheckRequest;
import com.fabricmanagement.common.platform.auth.dto.VerifyAndRegisterRequest;
import com.fabricmanagement.common.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.common.platform.auth.infra.repository.RefreshTokenRepository;
import com.fabricmanagement.common.platform.communication.app.ContactService;
import com.fabricmanagement.common.platform.organization.domain.Organization;
import com.fabricmanagement.common.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.common.platform.tenant.domain.Tenant;
import com.fabricmanagement.common.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.common.platform.user.api.facade.UserFacade;
import com.fabricmanagement.common.platform.user.app.UserContactAssignmentService;
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
  private final TenantRepository tenantRepository;
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
      throw new IllegalArgumentException(
          "Your information is not registered. Our representative will contact you.");
    }

    // Check if user already has AuthUser (user-based authentication)
    if (authUserRepository.existsByUserId(user.getId())) {
      log.warn("User already registered: userId={}", user.getId());
      throw new IllegalArgumentException("This account is already registered. Please login.");
    }

    verificationCodeManager.issueCode(request.getContactValue(), VerificationType.REGISTRATION);

    log.info(
        "Verification code queued for sending (async) to: {}",
        PiiMaskingUtil.maskEmail(request.getContactValue()));

    return "Verification code sent. Please check your email.";
  }

  @Transactional
  public LoginResponse verifyAndRegister(VerifyAndRegisterRequest request, String ipAddress) {
    log.info(
        "Verifying and registering: contactValue={}",
        PiiMaskingUtil.maskEmail(request.getContactValue()));

    verificationCodeManager.validateAndConsume(
        request.getContactValue(), VerificationType.REGISTRATION, request.getCode());

    UserDto user =
        userFacade
            .findByContactValue(request.getContactValue())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    // TOCTOU guard: re-check AuthUser doesn't exist (may have been created between check & verify)
    if (authUserRepository.existsByUserId(user.getId())) {
      throw new IllegalArgumentException("This account is already registered. Please login.");
    }

    // Tenant status guard: ensure tenant is active before allowing registration
    Tenant tenant =
        tenantRepository
            .findActiveById(user.getTenantId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Tenant is not active. Registration is not allowed."));
    if (!tenant.getStatus().hasAccess()) {
      throw new IllegalStateException(
          "Tenant account is " + tenant.getStatus() + ". Registration is not allowed.");
    }

    com.fabricmanagement.common.platform.communication.domain.Contact contact =
        findUserContactByValue(user.getId(), request.getContactValue());

    contactService.verifyContact(contact.getId());

    String passwordHash = passwordEncoder.encode(request.getPassword());

    // ✅ Create AuthUser for User (user-based authentication)
    // Multi-contact login supported: Any verified contact of this User can be used for login
    AuthUser authUser = AuthUser.create(user.getId(), passwordHash);
    authUser.setTenantId(user.getTenantId());
    authUser.verify();
    authUserRepository.save(authUser);

    com.fabricmanagement.common.platform.user.domain.User userEntity =
        userRepository
            .findByTenantIdAndId(user.getTenantId(), user.getId())
            .orElseThrow(() -> new IllegalStateException("User not found after registration"));

    String accessToken = jwtService.generateAccessToken(userEntity);
    String refreshToken = jwtService.generateRefreshToken(userEntity);

    // Persist refresh token for token refresh flow
    RefreshToken refreshTokenEntity =
        RefreshToken.create(
            user.getId(), refreshToken, Instant.now().plusMillis(refreshTokenExpiration));
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
        .companyName(orgOpt.map(Organization::getName).orElse(null))
        .taxId(orgOpt.map(Organization::getTaxId).orElse(null))
        .companyType(orgOpt.map(o -> o.getOrganizationType().name()).orElse(null))
        .build();
  }

  private com.fabricmanagement.common.platform.communication.domain.Contact findUserContactByValue(
      UUID userId, String contactValue) {
    // Batch load all contacts for user to avoid N+1 queries
    var userContacts = userContactAssignmentService.getUserContacts(userId);
    var contactIds = userContacts.stream().map(uc -> uc.getContactId()).toList();
    return contactService.findAllById(contactIds).stream()
        .filter(c -> c.getContactValue().equals(contactValue))
        .findFirst()
        .or(() -> contactService.findByValue(contactValue))
        .orElseThrow(() -> new IllegalStateException("Contact not found for user"));
  }
}
