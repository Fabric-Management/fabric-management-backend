package com.fabricmanagement.common.platform.auth.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.auth.domain.AuthUser;
import com.fabricmanagement.common.platform.auth.domain.RegistrationToken;
import com.fabricmanagement.common.platform.auth.domain.event.UserRegisteredEvent;
import com.fabricmanagement.common.platform.auth.dto.LoginResponse;
import com.fabricmanagement.common.platform.auth.dto.PasswordSetupRequest;
import com.fabricmanagement.common.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.common.platform.auth.infra.repository.RegistrationTokenRepository;
import com.fabricmanagement.common.platform.communication.app.ContactService;
import com.fabricmanagement.common.platform.communication.app.UserContactService;
import com.fabricmanagement.common.platform.communication.domain.Contact;
import com.fabricmanagement.common.platform.user.api.facade.UserFacade;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Password Setup Service - Complete registration with secure token.
 *
 * <h2>Two Flows:</h2>
 *
 * <ul>
 *   <li><b>Sales-led:</b> Token only (email verified by click)
 *   <li><b>Self-service:</b> Token + verification code (double security)
 * </ul>
 *
 * <p>Both flows result in:
 *
 * <ul>
 *   <li>Password set
 *   <li>User verified
 *   <li>Auto-login (JWT tokens returned)
 *   <li>Onboarding status check
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordSetupService {

  private final RegistrationTokenRepository tokenRepository;
  private final AuthUserRepository authUserRepository;
  private final UserFacade userFacade;
  private final UserRepository userRepository;
  private final UserContactService userContactService;
  private final ContactService contactService;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final DomainEventPublisher eventPublisher;
  private final EntityManager entityManager;

  @Value("${application.jwt.refresh-expiration:604800000}")
  private long refreshTokenExpiration;

  @Value("${application.jwt.expiration:900000}")
  private long accessTokenExpiration;

  /**
   * Complete password setup using secure token.
   *
   * <p><b>Two flows supported:</b>
   *
   * <ul>
   *   <li><b>Sales-led:</b> Token only (email verified by click)
   *   <li><b>Self-service:</b> Token + verification code (double security)
   * </ul>
   *
   * @param request Password setup request
   * @return Login response with tokens and onboarding status
   */
  @Transactional
  public LoginResponse setupPassword(PasswordSetupRequest request) {
    log.info(
        "Password setup initiated: token={}..., tokenType={}",
        request.getToken().substring(0, Math.min(8, request.getToken().length())),
        "checking...");

    RegistrationToken token =
        tokenRepository
            .findByToken(request.getToken())
            .orElseThrow(() -> new IllegalArgumentException("Invalid registration token"));

    if (!token.isValid()) {
      log.warn("Token invalid or expired: token={}", request.getToken().substring(0, 8) + "...");
      throw new IllegalArgumentException("Registration token is invalid or expired");
    }

    log.debug("Token type: {}", token.getTokenType());

    // Both SALES_LED and SELF_SERVICE tokens work the same way:
    // Email link click = email verified (no verification code needed)
    // Verification codes are only needed for unverified contacts during login flows

    UserDto user =
        userFacade
            .findByContactValue(token.getContactValue())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    // ✅ Check if user already has AuthUser (user-based authentication)
    if (authUserRepository.existsByUserId(user.getId())) {
      throw new IllegalArgumentException("User already has password set");
    }

    // Reload User entity with contacts/departments for JWT generation
    com.fabricmanagement.common.platform.user.domain.User userEntity =
        userRepository
            .findByTenantIdAndContactValue(user.getTenantId(), token.getContactValue())
            .orElseGet(
                () ->
                    userRepository
                        .findByTenantIdAndId(user.getTenantId(), user.getId())
                        .orElseThrow(() -> new IllegalArgumentException("User entity not found")));

    // ✅ Ensure user has at least one verified contact (for multi-contact login support)
    // Any verified contact can be used for login with user-based AuthUser
    UUID userEntityTenantId = userEntity.getTenantId();
    UUID userEntityId = userEntity.getId();

    if (userEntity.getAnyVerifiedContact().isEmpty()) {
      ensureAuthenticationContact(userEntityTenantId, userEntityId, token.getContactValue());
      // Reload user entity after contact recovery
      entityManager.clear();
      userEntity =
          userRepository
              .findByTenantIdAndContactValue(userEntityTenantId, token.getContactValue())
              .orElseGet(
                  () ->
                      userRepository
                          .findByTenantIdAndId(userEntityTenantId, userEntityId)
                          .orElseThrow(
                              () -> new IllegalArgumentException("User entity not found")));
    }

    // ✅ Create single AuthUser for User (user-based authentication)
    // Multi-contact login supported: Any verified contact of this User can be used for login
    String passwordHash = passwordEncoder.encode(request.getPassword());

    // ✅ Create AuthUser for User (one AuthUser per User)
    AuthUser authUser = AuthUser.create(user.getId(), passwordHash);
    authUser.setTenantId(user.getTenantId());
    authUser.verify();

    authUserRepository.save(authUser);
    log.info("✅ Password setup: Created AuthUser for user: userId={}", user.getId());

    token.markAsUsed();
    tokenRepository.save(token);

    // User entity already loaded with contacts/departments for JWT generation
    // If contact was recovered, userEntity was reloaded above
    com.fabricmanagement.common.platform.user.domain.User freshUserEntity = userEntity;

    String accessToken = jwtService.generateAccessToken(freshUserEntity);
    String refreshToken = jwtService.generateRefreshToken(freshUserEntity);

    // Get contact value for event
    String contactValue =
        freshUserEntity
            .getAnyVerifiedContact()
            .map(contact -> contact.getContactValue())
            .orElse(token.getContactValue());

    eventPublisher.publish(new UserRegisteredEvent(user.getTenantId(), user.getId(), contactValue));

    UserDto freshUser = UserDto.from(freshUserEntity);

    log.info(
        "✅ Password setup completed: user={}, needsOnboarding={}",
        PiiMaskingUtil.maskEmail(contactValue),
        !user.getHasCompletedOnboarding());

    return LoginResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .expiresIn(accessTokenExpiration / 1000) // Convert ms to seconds
        .user(freshUser)
        .needsOnboarding(!user.getHasCompletedOnboarding())
        .build();
  }

  /**
   * Ensure user has a verified contact for authentication.
   *
   * <p>Creates and verifies contact if missing, then assigns to user. Must run within correct
   * tenant context.
   */
  private UUID ensureAuthenticationContact(UUID tenantId, UUID userId, String contactValue) {
    log.warn("No verified contact found for userId={}, attempting recovery", userId);

    return TenantContext.executeInTenantContext(
        tenantId,
        () -> {
          Contact contact =
              contactService
                  .findByValue(contactValue)
                  .orElseGet(
                      () -> {
                        log.info("Creating contact for {}", PiiMaskingUtil.maskEmail(contactValue));
                        return contactService.createContact(
                            contactValue, null, "Primary", true, null);
                      });

          contactService.verifyContact(contact.getId());

          if (!userContactService.existsUserContact(userId, contact.getId())) {
            userContactService.assignContact(userId, contact.getId(), true);
          } else {
            userContactService.setAsDefault(userId, contact.getId());
          }

          log.info("Recovered verified contact: userId={}, contactId={}", userId, contact.getId());
          return contact.getId();
        });
  }
}
