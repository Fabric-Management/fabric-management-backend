package com.fabricmanagement.common.platform.auth.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.platform.auth.app.VerificationCodeManager.IssuedVerificationCode;
import com.fabricmanagement.common.platform.auth.domain.AuthUser;
import com.fabricmanagement.common.platform.auth.domain.RefreshToken;
import com.fabricmanagement.common.platform.auth.domain.VerificationType;
import com.fabricmanagement.common.platform.auth.domain.event.UserLoginEvent;
import com.fabricmanagement.common.platform.auth.dto.LoginResponse;
import com.fabricmanagement.common.platform.auth.dto.MaskedContactInfo;
import com.fabricmanagement.common.platform.auth.dto.PasswordResetRequest;
import com.fabricmanagement.common.platform.auth.dto.PasswordResetVerifyRequest;
import com.fabricmanagement.common.platform.auth.dto.UserContactInfoResponse;
import com.fabricmanagement.common.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.common.platform.auth.infra.repository.RefreshTokenRepository;
import com.fabricmanagement.common.platform.communication.app.ContactService;
import com.fabricmanagement.common.platform.communication.app.UserContactService;
import com.fabricmanagement.common.platform.communication.app.VerificationService;
import com.fabricmanagement.common.platform.company.api.facade.CompanyFacade;
import com.fabricmanagement.common.platform.company.dto.CompanyDto;
import com.fabricmanagement.common.platform.user.api.facade.UserFacade;
import com.fabricmanagement.common.platform.user.domain.ContactType;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Password Reset Service - Handles complete password reset flow.
 *
 * <h2>Complete Flow:</h2>
 *
 * <ol>
 *   <li>Get masked contacts for user (only verified) - Multiple contacts supported
 *   <li>User selects a contact (by authUserId for performance)
 *   <li>Send verification code to selected contact
 *   <li>User enters verification code + new password
 *   <li>Verify code and reset password
 *   <li>Auto-login with new credentials
 * </ol>
 *
 * <h2>Security Features:</h2>
 *
 * <ul>
 *   <li>✅ Enumeration attack prevention (masked contacts)
 *   <li>✅ Only verified contacts shown
 *   <li>✅ Performance optimized (authUserId direct lookup)
 *   <li>✅ Context-aware error messages
 *   <li>✅ Multi-channel verification code delivery
 *   <li>✅ Verification code expiry and attempt limits
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

  private final AuthUserRepository authUserRepository;
  private final VerificationCodeManager verificationCodeManager;
  private final RefreshTokenRepository refreshTokenRepository;
  private final ContactService contactService;
  private final UserContactService userContactService;
  private final UserRepository userRepository;
  private final UserFacade userFacade;
  private final CompanyFacade companyFacade;
  private final VerificationService verificationService;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final DomainEventPublisher eventPublisher;

  @Value("${application.jwt.refresh-expiration:604800000}")
  private long refreshTokenExpiration;

  /**
   * Get masked contact information for password reset.
   *
   * <p>Returns all verified contacts for the user (email, phone, etc.) to improve UX.
   *
   * <p>Strategy:
   *
   * <ul>
   *   <li>Find the User by contactValue (the one used for login attempt)
   *   <li>Find all AuthUser records with same contactValue patterns (email/phone from same user)
   *   <li>Filter only verified AuthUsers
   *   <li>Mask and return their contact values with authUserId for direct lookup
   * </ul>
   *
   * @param contactValue The contact value used in login attempt
   * @return List of masked contact information with authUserId
   */
  @Transactional(readOnly = true)
  public UserContactInfoResponse getMaskedContacts(String contactValue) {
    log.info(
        "Getting masked contacts for password reset: contactValue={}",
        PiiMaskingUtil.maskEmail(contactValue));

    // Find the user who attempted login
    Optional<UserDto> userOpt = userFacade.findByContactValue(contactValue);

    if (userOpt.isEmpty()) {
      log.warn("User not found for contact: {}", PiiMaskingUtil.maskEmail(contactValue));
      return UserContactInfoResponse.builder().contacts(new ArrayList<>()).build();
    }

    List<MaskedContactInfo> maskedContacts = new ArrayList<>();

    // ✅ Find AuthUser by contact value (user-based authentication)
    // This finds User via Contact → UserContact → User, then returns User's AuthUser
    Optional<AuthUser> authUserOpt = authUserRepository.findByContactValue(contactValue);
    if (authUserOpt.isPresent() && authUserOpt.get().getIsVerified()) {
      AuthUser authUser = authUserOpt.get();
      UUID userId = authUser.getUserId();

      // ✅ Get User's verified contacts (user-based authentication)
      // All verified contacts can be used for password reset
      List<com.fabricmanagement.common.platform.communication.domain.UserContact> userContacts =
          userContactService.getUserContacts(userId);

      // Get all verified contacts for this user
      for (com.fabricmanagement.common.platform.communication.domain.UserContact userContact :
          userContacts) {
        com.fabricmanagement.common.platform.communication.domain.Contact contact =
            contactService.findById(userContact.getContactId()).orElse(null);

        if (contact != null && Boolean.TRUE.equals(contact.getIsVerified())) {
          // Map Communication ContactType to User ContactType
          ContactType userContactType = mapToUserContactType(contact.getContactType());
          maskedContacts.add(
              createMaskedContact(authUser.getId(), contact.getContactValue(), userContactType));
        }
      }
    }

    log.info("Found {} verified contacts for user", maskedContacts.size());

    return UserContactInfoResponse.builder().contacts(maskedContacts).build();
  }

  /**
   * Request password reset - Send verification code to selected contact.
   *
   * <p>Now uses authUserId for direct lookup (performance optimization).
   *
   * @param request Password reset request with authUserId
   * @return Success message
   */
  @Transactional
  public String requestPasswordReset(PasswordResetRequest request) {
    log.info(
        "Password reset request: authUserId={}, contactType={}",
        request.getAuthUserId(),
        request.getContactType());

    // ✅ Direct lookup by authUserId (user-based authentication)
    AuthUser authUser =
        authUserRepository
            .findById(request.getAuthUserId())
            .orElseThrow(
                () ->
                    createContextAwarePasswordResetException(
                        "Contact not found. Please try again or contact support."));

    if (!authUser.getIsVerified()) {
      throw createContextAwarePasswordResetException(
          "Account is not verified. Please verify your account first.");
    }

    // ✅ Get User's verified contacts (user-based authentication)
    UUID userId = authUser.getUserId();
    List<com.fabricmanagement.common.platform.communication.domain.UserContact> userContacts =
        userContactService.getUserContacts(userId);

    // Find contact matching requested type
    ContactType requestedType = ContactType.valueOf(request.getContactType());
    com.fabricmanagement.common.platform.communication.domain.Contact selectedContact = null;

    for (com.fabricmanagement.common.platform.communication.domain.UserContact userContact :
        userContacts) {
      com.fabricmanagement.common.platform.communication.domain.Contact contact =
          contactService.findById(userContact.getContactId()).orElse(null);

      if (contact != null
          && Boolean.TRUE.equals(contact.getIsVerified())
          && mapToUserContactType(contact.getContactType()) == requestedType) {
        selectedContact = contact;
        break;
      }
    }

    if (selectedContact == null) {
      throw new IllegalArgumentException(
          "No verified "
              + request.getContactType().toLowerCase()
              + " contact found for this account.");
    }

    String contactValue = selectedContact.getContactValue();

    IssuedVerificationCode issuedCode;
    try {
      issuedCode = verificationCodeManager.issueCode(contactValue, VerificationType.PASSWORD_RESET);
    } catch (IllegalArgumentException | IllegalStateException ex) {
      log.warn(
          "Password reset verification throttled: contactValue={}, reason={}",
          PiiMaskingUtil.maskEmail(contactValue),
          ex.getMessage());
      throw createContextAwarePasswordResetException(contactValue, ex.getMessage());
    }

    // Send verification code via multi-channel (WhatsApp → Email → SMS)
    try {
      verificationService.sendVerificationCode(contactValue, issuedCode.code());
      log.info(
          "✅ Password reset code sent successfully to: {}", PiiMaskingUtil.maskEmail(contactValue));
    } catch (Exception e) {
      log.error(
          "❌ Failed to send password reset code to: {}", PiiMaskingUtil.maskEmail(contactValue), e);
      // Continue anyway - code is in database, user can try again
    }

    String contactTypeDisplay = request.getContactType().equals("EMAIL") ? "email" : "phone";
    return String.format(
        "Password reset verification code has been sent to your %s.", contactTypeDisplay);
  }

  /**
   * Verify password reset code and reset password.
   *
   * <p>Complete password reset flow:
   *
   * <ol>
   *   <li>Validate verification code (expiry, attempts, type)
   *   <li>Verify authUserId matches
   *   <li>Hash and update password
   *   <li>Unlock account if locked
   *   <li>Reset failed login attempts
   *   <li>Generate JWT tokens (auto-login)
   *   <li>Publish login event
   * </ol>
   *
   * @param request Password reset verification request
   * @return Login response with tokens (auto-login after password reset)
   */
  @Transactional
  public LoginResponse verifyAndResetPassword(PasswordResetVerifyRequest request) {
    log.info(
        "Password reset verification: authUserId={}, code={}",
        request.getAuthUserId(),
        "******"); // Never log verification codes

    // ✅ Find AuthUser by ID (user-based authentication)
    AuthUser authUser =
        authUserRepository
            .findById(request.getAuthUserId())
            .orElseThrow(() -> new IllegalArgumentException("Invalid authentication information"));

    if (!authUser.getIsVerified()) {
      throw new IllegalArgumentException("Account is not verified");
    }

    // ✅ Get User's verified contacts to find contactValue for verification code validation
    UUID userId = authUser.getUserId();
    List<com.fabricmanagement.common.platform.communication.domain.UserContact> userContacts =
        userContactService.getUserContacts(userId);

    // Find any verified contact for verification code validation
    String contactValue = null;
    for (com.fabricmanagement.common.platform.communication.domain.UserContact userContact :
        userContacts) {
      com.fabricmanagement.common.platform.communication.domain.Contact contact =
          contactService.findById(userContact.getContactId()).orElse(null);

      if (contact != null && Boolean.TRUE.equals(contact.getIsVerified())) {
        contactValue = contact.getContactValue();
        break; // Use first verified contact for verification code validation
      }
    }

    if (contactValue == null) {
      throw new IllegalArgumentException(
          "No verified contact found for verification code validation");
    }

    verificationCodeManager.validateAndConsume(
        contactValue, VerificationType.PASSWORD_RESET, request.getCode());

    // Check if new password is same as old password
    if (passwordEncoder.matches(request.getNewPassword(), authUser.getPasswordHash())) {
      log.warn("New password same as old password: userId={}", userId);
      throw new IllegalArgumentException(
          "New password must be different from your current password");
    }

    // Update password
    String newPasswordHash = passwordEncoder.encode(request.getNewPassword());
    authUser.changePassword(newPasswordHash);

    // Unlock account if locked and reset failed attempts
    authUser.unlock();

    authUserRepository.save(authUser);

    // ✅ Get User DTO (user-based authentication)
    UserDto user =
        userFacade
            .findById(authUser.getTenantId(), userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    if (!user.getIsActive()) {
      throw new IllegalArgumentException("User account is deactivated");
    }

    // Get User entity with contacts/departments for JWT generation
    com.fabricmanagement.common.platform.user.domain.User userEntity =
        userRepository
            .findByTenantIdAndId(user.getTenantId(), user.getId())
            .orElseThrow(() -> new IllegalArgumentException("User entity not found"));

    // Generate JWT tokens
    String accessToken = jwtService.generateAccessToken(userEntity);
    String refreshToken = jwtService.generateRefreshToken(userEntity);

    // Save refresh token
    RefreshToken refreshTokenEntity =
        RefreshToken.create(
            user.getId(), refreshToken, Instant.now().plusMillis(refreshTokenExpiration));
    refreshTokenRepository.save(refreshTokenEntity);

    // Publish login event (password reset is considered successful login)
    eventPublisher.publish(
        new UserLoginEvent(
            user.getTenantId(),
            user.getId(),
            contactValue,
            "password-reset" // IP address placeholder
            ));

    log.info(
        "✅ Password reset completed successfully: userId={}, uid={}", user.getId(), user.getUid());

    return LoginResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .expiresIn(900L) // 15 minutes in seconds
        .user(user)
        .needsOnboarding(!user.getHasCompletedOnboarding())
        .build();
  }

  /**
   * Create masked contact info from authUser ID, contact value and type. Includes authUserId for
   * direct lookup performance optimization.
   */
  private MaskedContactInfo createMaskedContact(
      UUID authUserId, String contactValue, ContactType contactType) {
    String maskedValue;

    if (contactType == ContactType.EMAIL) {
      maskedValue = PiiMaskingUtil.maskEmail(contactValue);
    } else {
      maskedValue = PiiMaskingUtil.maskPhone(contactValue);
    }

    return MaskedContactInfo.builder()
        .authUserId(authUserId) // ✅ Performance optimization
        .maskedValue(maskedValue)
        .type(contactType.name())
        .verified(true) // Only verified contacts are returned
        .build();
  }

  /**
   * Create context-aware password reset exception.
   *
   * <p>Provides helpful error messages based on user context (tenant vs supplier). Similar to
   * LoginService.createContextAwareNotFoundException pattern.
   */
  private IllegalArgumentException createContextAwarePasswordResetException(
      String contactValue, String defaultMessage) {
    Optional<UserDto> userOpt = userFacade.findByContactValue(contactValue);

    if (userOpt.isEmpty()) {
      return new IllegalArgumentException(defaultMessage);
    }

    UserDto user = userOpt.get();
    Optional<CompanyDto> companyOpt =
        companyFacade.findById(user.getTenantId(), user.getCompanyId());

    if (companyOpt.isPresent()) {
      CompanyDto company = companyOpt.get();

      if (company.getIsTenant()) {
        return new IllegalArgumentException(
            defaultMessage
                + " If you're a "
                + company.getCompanyName()
                + " employee, please contact your IT team for assistance.");
      } else {
        return new IllegalArgumentException(
            defaultMessage
                + " Please contact your customer representative at "
                + company.getCompanyName()
                + " for assistance.");
      }
    }

    return new IllegalArgumentException(defaultMessage);
  }

  /** Create context-aware password reset exception (overload without contactValue lookup). */
  private IllegalArgumentException createContextAwarePasswordResetException(String message) {
    return new IllegalArgumentException(message);
  }

  /** Map Communication module ContactType to User module ContactType. */
  private ContactType mapToUserContactType(
      com.fabricmanagement.common.platform.communication.domain.ContactType commType) {
    return switch (commType) {
      case EMAIL -> ContactType.EMAIL;
      case MOBILE, LANDLINE, PHONE_EXTENSION -> ContactType.PHONE;
      default -> ContactType.EMAIL;
    };
  }
}
