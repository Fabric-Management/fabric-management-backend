package com.fabricmanagement.common.platform.auth.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.platform.auth.domain.AuthUser;
import com.fabricmanagement.common.platform.auth.domain.VerificationType;
import com.fabricmanagement.common.platform.auth.domain.event.UserRegisteredEvent;
import com.fabricmanagement.common.platform.auth.dto.LoginResponse;
import com.fabricmanagement.common.platform.auth.dto.OnboardingPrefillDto;
import com.fabricmanagement.common.platform.auth.dto.RegisterCheckRequest;
import com.fabricmanagement.common.platform.auth.dto.VerifyAndRegisterRequest;
import com.fabricmanagement.common.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.common.platform.communication.app.ContactService;
import com.fabricmanagement.common.platform.company.domain.Company;
import com.fabricmanagement.common.platform.company.infra.repository.CompanyRepository;
import com.fabricmanagement.common.platform.user.api.facade.UserFacade;
import com.fabricmanagement.common.platform.user.app.UserContactAssignmentService;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.common.util.PiiMaskingUtil;
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
  private final VerificationCodeManager verificationCodeManager;
  private final CompanyRepository companyRepository;

  // VerificationService no longer injected - VerificationCodeManager.issueCode sends via dispatcher

  @Value("${application.jwt.expiration:900000}")
  private long accessTokenExpirationMs;

  @Transactional
  public String checkEligibilityAndSendCode(RegisterCheckRequest request) {
    log.info(
        "Checking registration eligibility: contactValue={}",
        PiiMaskingUtil.maskEmail(request.getContactValue()));

    UserDto user = userFacade.findByContactValue(request.getContactValue()).orElse(null);

    if (user == null) {
      log.warn(
          "Contact not found in system: {}", PiiMaskingUtil.maskEmail(request.getContactValue()));
      return "Your information is not registered. Our representative will contact you.";
    }

    // ✅ Check if user already has AuthUser (user-based authentication)
    if (authUserRepository.existsByUserId(user.getId())) {
      log.warn("User already registered: userId={}", user.getId());
      return "This account is already registered. Please login.";
    }

    try {
      verificationCodeManager.issueCode(request.getContactValue(), VerificationType.REGISTRATION);
    } catch (IllegalArgumentException | IllegalStateException ex) {
      log.warn(
          "Verification code issuance throttled: contactValue={}, reason={}",
          PiiMaskingUtil.maskEmail(request.getContactValue()),
          ex.getMessage());
      return ex.getMessage();
    }

    log.info(
        "Verification code queued for sending (async) to: {}",
        PiiMaskingUtil.maskEmail(request.getContactValue()));

    return "Verification code sent. Please check your email.";
  }

  @Transactional
  public LoginResponse verifyAndRegister(VerifyAndRegisterRequest request) {
    log.info(
        "Verifying and registering: contactValue={}",
        PiiMaskingUtil.maskEmail(request.getContactValue()));

    verificationCodeManager.validateAndConsume(
        request.getContactValue(), VerificationType.REGISTRATION, request.getCode());

    UserDto user =
        userFacade
            .findByContactValue(request.getContactValue())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

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

    String contactValue = contact.getContactValue();
    eventPublisher.publish(new UserRegisteredEvent(user.getTenantId(), user.getId(), contactValue));

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
    Optional<Company> companyOpt =
        companyRepository.findByTenantIdAndId(user.getTenantId(), user.getCompanyId());
    return OnboardingPrefillDto.builder()
        .primaryEmail(primaryEmail)
        .companyName(companyOpt.map(Company::getCompanyName).orElse(null))
        .taxId(companyOpt.map(Company::getTaxId).orElse(null))
        .companyType(companyOpt.map(c -> c.getCompanyType().name()).orElse(null))
        .build();
  }

  private com.fabricmanagement.common.platform.communication.domain.Contact findUserContactByValue(
      UUID userId, String contactValue) {
    return userContactAssignmentService.getUserContacts(userId).stream()
        .map(uc -> contactService.findById(uc.getContactId()))
        .flatMap(Optional::stream)
        .filter(c -> c.getContactValue().equals(contactValue))
        .findFirst()
        .or(() -> contactService.findByValue(contactValue))
        .orElseThrow(() -> new IllegalStateException("Verified contact not found for user"));
  }
}
