package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.util.DeviceInfoUtil;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import com.fabricmanagement.platform.auth.domain.AuthUser;
import com.fabricmanagement.platform.auth.domain.RefreshToken;
import com.fabricmanagement.platform.auth.domain.VerificationType;
import com.fabricmanagement.platform.auth.domain.event.UserLoginEvent;
import com.fabricmanagement.platform.auth.dto.LoginResponse;
import com.fabricmanagement.platform.auth.dto.MaskedContactInfo;
import com.fabricmanagement.platform.auth.dto.PasswordResetRequest;
import com.fabricmanagement.platform.auth.dto.PasswordResetVerifyRequest;
import com.fabricmanagement.platform.auth.dto.UserContactInfoResponse;
import com.fabricmanagement.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.platform.auth.infra.repository.RefreshTokenRepository;
import com.fabricmanagement.platform.communication.app.ContactService;
import com.fabricmanagement.platform.organization.api.facade.OrganizationFacade;
import com.fabricmanagement.platform.organization.dto.OrganizationDto;
import com.fabricmanagement.platform.user.api.facade.UserFacade;
import com.fabricmanagement.platform.user.app.UserContactAssignmentService;
import com.fabricmanagement.platform.user.domain.ContactType;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
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
  private final UserContactAssignmentService userContactAssignmentService;
  private final UserRepository userRepository;
  private final UserFacade userFacade;
  private final OrganizationFacade organizationFacade;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final DomainEventPublisher eventPublisher;

  @Value("${application.jwt.expiration:900000}")
  private long accessTokenExpiration;

  @Value("${application.jwt.refresh-expiration:604800000}")
  private long refreshTokenExpiration;

  /**
   * Get masked contact information for password reset.
   *
   * <p>Returns all verified contacts for the user (email, phone, etc.) to improve UX.
   *
   * <p>Strategy (industry best practice for public endpoints):
   *
   * <ul>
   *   <li>Tenant-less global search: find User by contactValue (no tenant context)
   *   <li>Switch context: run AuthUser lookup and contact resolution in user's tenant
   *   <li>Filter only verified contacts; mask and return with authUserId for direct lookup
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

    // 1. Tenant-less global search: find user by contact value (no tenant context)
    Optional<UserDto> userOpt = userFacade.findByContactValue(contactValue);

    if (userOpt.isEmpty()) {
      log.warn("User not found for contact: {}", PiiMaskingUtil.maskEmail(contactValue));
      return UserContactInfoResponse.builder().contacts(new ArrayList<>()).build();
    }

    UserDto user = userOpt.get();
    UUID tenantId = user.getTenantId();

    // 2. Switch context: run AuthUser lookup and contact resolution in user's tenant
    List<MaskedContactInfo> maskedContacts =
        TenantContext.executeInTenantContext(
            tenantId,
            () -> {
              List<MaskedContactInfo> list = new ArrayList<>();

              Optional<AuthUser> authUserOpt = authUserRepository.findByContactValue(contactValue);
              if (authUserOpt.isEmpty()
                  || !Boolean.TRUE.equals(authUserOpt.get().getIsVerified())) {
                return list;
              }

              AuthUser authUser = authUserOpt.get();
              UUID userId = authUser.getUserId();

              List<com.fabricmanagement.platform.user.domain.UserContact> userContacts =
                  userContactAssignmentService.getUserContacts(userId);

              for (com.fabricmanagement.platform.user.domain.UserContact userContact :
                  userContacts) {
                com.fabricmanagement.platform.communication.domain.Contact contact =
                    contactService.findById(userContact.getContactId()).orElse(null);

                if (contact != null && Boolean.TRUE.equals(contact.getIsVerified())) {
                  ContactType userContactType = mapToUserContactType(contact.getContactType());
                  list.add(
                      createMaskedContact(
                          authUser.getId(), contact.getContactValue(), userContactType));
                }
              }
              return list;
            });

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

    // ✅ Public endpoint: resolve contact in user's tenant context
    UUID userId = authUser.getUserId();
    UUID tenantId = authUser.getTenantId();
    ContactType requestedType = ContactType.valueOf(request.getContactType());

    String contactValue =
        TenantContext.executeInTenantContext(
            tenantId,
            () -> {
              List<com.fabricmanagement.platform.user.domain.UserContact> userContacts =
                  userContactAssignmentService.getUserContacts(userId);

              for (com.fabricmanagement.platform.user.domain.UserContact userContact :
                  userContacts) {
                com.fabricmanagement.platform.communication.domain.Contact contact =
                    contactService.findById(userContact.getContactId()).orElse(null);

                if (contact != null
                    && Boolean.TRUE.equals(contact.getIsVerified())
                    && mapToUserContactType(contact.getContactType()) == requestedType) {
                  return contact.getContactValue();
                }
              }
              return null;
            });

    if (contactValue == null) {
      throw new IllegalArgumentException(
          "No verified "
              + request.getContactType().toLowerCase()
              + " contact found for this account.");
    }

    try {
      verificationCodeManager.issueCode(contactValue, VerificationType.PASSWORD_RESET);
      log.info(
          "✅ Password reset code sent successfully to: {}", PiiMaskingUtil.maskEmail(contactValue));
    } catch (IllegalArgumentException | IllegalStateException ex) {
      log.warn(
          "Password reset verification throttled: contactValue={}, reason={}",
          PiiMaskingUtil.maskEmail(contactValue),
          ex.getMessage());
      throw createContextAwarePasswordResetException(contactValue, ex.getMessage());
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
  public LoginResponse verifyAndResetPassword(
      PasswordResetVerifyRequest request, String ipAddress, String userAgent) {
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

    // ✅ Public endpoint: resolve contact in user's tenant context
    UUID userId = authUser.getUserId();
    UUID tenantId = authUser.getTenantId();

    String contactValue =
        TenantContext.executeInTenantContext(
            tenantId,
            () -> {
              List<com.fabricmanagement.platform.user.domain.UserContact> userContacts =
                  userContactAssignmentService.getUserContacts(userId);
              for (com.fabricmanagement.platform.user.domain.UserContact userContact :
                  userContacts) {
                com.fabricmanagement.platform.communication.domain.Contact contact =
                    contactService.findById(userContact.getContactId()).orElse(null);
                if (contact != null && Boolean.TRUE.equals(contact.getIsVerified())) {
                  return contact.getContactValue();
                }
              }
              return null;
            });

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
    com.fabricmanagement.platform.user.domain.User userEntity =
        userRepository
            .findByTenantIdAndId(user.getTenantId(), user.getId())
            .orElseThrow(() -> new IllegalArgumentException("User entity not found"));

    // Generate JWT tokens
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
    refreshTokenRepository.save(refreshTokenEntity);

    // Publish login event (password reset is considered successful login)
    eventPublisher.publish(
        new UserLoginEvent(user.getTenantId(), user.getId(), contactValue, ipAddress));

    log.info(
        "✅ Password reset completed successfully: userId={}, uid={}", user.getId(), user.getUid());

    return LoginResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .expiresIn(accessTokenExpiration / 1000)
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
    Optional<OrganizationDto> orgOpt =
        organizationFacade.findById(user.getTenantId(), user.getOrganizationId());

    if (orgOpt.isPresent()) {
      OrganizationDto org = orgOpt.get();

      // Root organization (no parent) is the tenant's main organization
      if (org.getParentOrganizationId() == null) {
        return new IllegalArgumentException(
            defaultMessage
                + " If you're a "
                + org.getName()
                + " employee, please contact your IT team for assistance.");
      } else {
        return new IllegalArgumentException(
            defaultMessage
                + " Please contact your customer representative at "
                + org.getName()
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
      com.fabricmanagement.platform.communication.domain.ContactType commType) {
    return switch (commType) {
      case EMAIL -> ContactType.EMAIL;
      case MOBILE, LANDLINE, PHONE_EXTENSION -> ContactType.PHONE;
      default -> ContactType.EMAIL;
    };
  }
}
