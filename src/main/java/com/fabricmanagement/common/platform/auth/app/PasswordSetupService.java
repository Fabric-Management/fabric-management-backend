package com.fabricmanagement.common.platform.auth.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.auth.domain.AuthUser;
import com.fabricmanagement.common.platform.auth.domain.RefreshToken;
import com.fabricmanagement.common.platform.auth.domain.RegistrationToken;
import com.fabricmanagement.common.platform.auth.domain.event.UserLoginEvent;
import com.fabricmanagement.common.platform.auth.domain.event.UserRegisteredEvent;
import com.fabricmanagement.common.platform.auth.dto.LoginResponse;
import com.fabricmanagement.common.platform.auth.dto.OnboardingPrefillDto;
import com.fabricmanagement.common.platform.auth.dto.PasswordSetupRequest;
import com.fabricmanagement.common.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.common.platform.auth.infra.repository.RefreshTokenRepository;
import com.fabricmanagement.common.platform.auth.infra.repository.RegistrationTokenRepository;
import com.fabricmanagement.common.platform.communication.app.ContactService;
import com.fabricmanagement.common.platform.communication.domain.Contact;
import com.fabricmanagement.common.platform.organization.domain.Organization;
import com.fabricmanagement.common.platform.organization.infra.repository.OrganizationAddressRepository;
import com.fabricmanagement.common.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.common.platform.tenant.domain.Tenant;
import com.fabricmanagement.common.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.common.platform.user.api.facade.UserFacade;
import com.fabricmanagement.common.platform.user.app.UserContactAssignmentService;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import com.fabricmanagement.human.core.employee.application.EmployeeService;
import jakarta.persistence.EntityManager;
import java.time.Instant;
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
 *   <li><b>Sales-led:</b> Token only (email verified by link click)
 *   <li><b>Self-service:</b> Token only (email verified by link click)
 * </ul>
 *
 * <p>Both flows result in:
 *
 * <ul>
 *   <li>Password set
 *   <li>User verified
 *   <li>Auto-login (JWT tokens returned)
 *   <li>Onboarding status and prefill for onboarding form
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordSetupService {

  private final RegistrationTokenRepository tokenRepository;
  private final AuthUserRepository authUserRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final UserFacade userFacade;
  private final UserRepository userRepository;
  private final UserContactAssignmentService userContactAssignmentService;
  private final ContactService contactService;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final DomainEventPublisher eventPublisher;
  private final EntityManager entityManager;
  private final OrganizationRepository organizationRepository;
  private final OrganizationAddressRepository organizationAddressRepository;
  private final TenantRepository tenantRepository;
  private final EmployeeService employeeService;

  @Value("${application.jwt.refresh-expiration:604800000}")
  private long refreshTokenExpiration;

  @Value("${application.jwt.expiration:900000}")
  private long accessTokenExpiration;

  /**
   * Complete password setup using secure token.
   *
   * <p><b>Both flows:</b> Token only (email verified by link click). No verification code.
   *
   * @param request Password setup request
   * @return Login response with tokens, onboarding status and onboardingPrefill when needed
   */
  @Transactional
  public LoginResponse setupPassword(PasswordSetupRequest request, String ipAddress) {
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

    // Email is verified when user completes password setup (link click + password set).
    // ensureAuthenticationContact below verifies the contact for this flow.

    UserDto user =
        userFacade
            .findByContactValue(token.getContactValue())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    // ✅ Check if user already has AuthUser (user-based authentication)
    if (authUserRepository.existsByUserId(user.getId())) {
      throw new IllegalArgumentException("User already has password set");
    }

    // Tenant status guard: ensure tenant is active before allowing registration
    Tenant tenant =
        tenantRepository
            .findActiveById(user.getTenantId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Tenant is not active. Password setup is not allowed."));
    if (!tenant.getStatus().hasAccess()) {
      throw new IllegalStateException(
          "Tenant account is " + tenant.getStatus() + ". Password setup is not allowed.");
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

    // ✅ Verify contact when user completes password setup (link click = email ownership proof).
    // Ensures user has at least one verified contact for multi-contact login support.
    UUID userEntityTenantId = userEntity.getTenantId();
    UUID userEntityId = userEntity.getId();

    if (userEntity.getAnyVerifiedContact().isEmpty()) {
      ensureAuthenticationContact(userEntityTenantId, userEntityId, token.getContactValue());
      // Detach stale user entity and reload with fresh contact associations
      entityManager.detach(userEntity);
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

    // Persist refresh token for token refresh flow
    RefreshToken refreshTokenEntity =
        RefreshToken.create(
            user.getId(), refreshToken, Instant.now().plusMillis(refreshTokenExpiration));
    refreshTokenEntity.setTenantId(user.getTenantId());
    refreshTokenRepository.save(refreshTokenEntity);

    // Get contact value for event
    String contactValue =
        freshUserEntity
            .getAnyVerifiedContact()
            .map(contact -> contact.getContactValue())
            .orElse(token.getContactValue());

    eventPublisher.publish(new UserRegisteredEvent(user.getTenantId(), user.getId(), contactValue));
    // Auto-login event for audit trail (password setup is a successful login)
    eventPublisher.publish(
        new UserLoginEvent(user.getTenantId(), user.getId(), contactValue, ipAddress));

    UserDto freshUser =
        UserDto.from(
            freshUserEntity,
            employeeService
                .getEmployeeByUserId(freshUserEntity.getTenantId(), freshUserEntity.getId())
                .orElse(null));

    boolean needsOnboarding = !Boolean.TRUE.equals(user.getHasCompletedOnboarding());
    OnboardingPrefillDto onboardingPrefill = null;
    if (needsOnboarding) {
      var orgOpt =
          organizationRepository.findByTenantIdAndId(user.getTenantId(), user.getOrganizationId());
      var prefillBuilder =
          OnboardingPrefillDto.builder()
              .primaryEmail(contactValue)
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
              .organizationType(orgOpt.map(o -> o.getOrganizationType().name()).orElse(null));

      // Sales-led: prefill address from Organization primary address; self-signup: no address
      organizationAddressRepository
          .findPrimaryWithAddressByOrganizationId(user.getOrganizationId())
          .map(oa -> oa.getAddress())
          .ifPresent(
              addr -> {
                prefillBuilder
                    .addressLine1(addr.getStreetAddress())
                    .addressLine2(addr.getAddressLine2())
                    .city(addr.getCity())
                    .state(addr.getState())
                    .district(addr.getDistrict())
                    .postalCode(addr.getPostalCode())
                    .country(addr.getCountry());
              });

      onboardingPrefill = prefillBuilder.build();
    }

    log.info(
        "✅ Password setup completed: user={}, needsOnboarding={}",
        PiiMaskingUtil.maskEmail(contactValue),
        needsOnboarding);

    return LoginResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .expiresIn(accessTokenExpiration / 1000) // Convert ms to seconds
        .user(freshUser)
        .needsOnboarding(needsOnboarding)
        .onboardingPrefill(onboardingPrefill)
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

          if (!userContactAssignmentService.existsUserContact(userId, contact.getId())) {
            userContactAssignmentService.assignContact(userId, contact.getId(), true);
          } else {
            userContactAssignmentService.setAsDefault(userId, contact.getId());
          }

          log.info("Recovered verified contact: userId={}, contactId={}", userId, contact.getId());
          return contact.getId();
        });
  }
}
