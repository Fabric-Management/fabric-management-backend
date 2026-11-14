package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.persistence.TenantSessionBinder;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import com.fabricmanagement.platform.auth.domain.AuthUser;
import com.fabricmanagement.platform.auth.domain.LoginIdentity;
import com.fabricmanagement.platform.auth.domain.RefreshToken;
import com.fabricmanagement.platform.auth.domain.event.UserLoginEvent;
import com.fabricmanagement.platform.auth.domain.event.UserRegisteredEvent;
import com.fabricmanagement.platform.auth.dto.LoginResponse;
import com.fabricmanagement.platform.auth.dto.OnboardingPrefillDto;
import com.fabricmanagement.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.platform.auth.infra.repository.RefreshTokenRepository;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.app.ContactService;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationAddressRepository;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.user.app.UserContactAssignmentService;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.port.EmployeeProjectionPort;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
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
 * The last part of every setup link: give an admin user their login and, when that is safe, sign
 * them in. Shared by the self-service signup link ({@link SignupCompletionService}) and the
 * registration-token links of sales-led and invited users ({@link PasswordSetupService}).
 *
 * <p>The link has proved that the person controls the e-mail address. What follows depends on
 * whether the address already has a login identity (identities are global, one per address):
 *
 * <ul>
 *   <li><b>No identity yet:</b> a password is required. The identity and its membership are created
 *       and the person is signed in.
 *   <li><b>An identity exists</b> (the person was invited to another company first): only the
 *       membership is added. The existing password is kept and nobody is forced to reset it. The
 *       person is <em>not</em> signed in by the link, because that would skip the password and MFA
 *       of the existing login; the response says {@code signInRequired} and the client sends them
 *       to the normal login.
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountSetupService {

  /**
   * The admin user the link is for. {@code needsOnboarding} is whether the person still has to fill
   * in the company profile after signing in.
   */
  public record SetupTarget(
      UUID tenantId,
      UUID userId,
      UUID organizationId,
      String contactValue,
      boolean needsOnboarding) {}

  private final AuthUserRepository authUserRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final UserRepository userRepository;
  private final UserContactAssignmentService userContactAssignmentService;
  private final ContactService contactService;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final DomainEventPublisher eventPublisher;
  private final EntityManager entityManager;
  private final OrganizationRepository organizationRepository;
  private final OrganizationAddressRepository organizationAddressRepository;
  private final EmployeeProjectionPort employeeProjectionPort;
  private final TenantSessionBinder tenantSessionBinder;
  private final IdentityProvisioningService identityProvisioningService;

  @Value("${application.jwt.refresh-expiration:604800000}")
  private long refreshTokenExpiration;

  @Value("${application.jwt.expiration:900000}")
  private long accessTokenExpiration;

  /**
   * @param rawPassword the password the person chose; ignored (and not required) when the address
   *     already has a login identity
   */
  @Transactional
  public LoginResponse finishSetup(SetupTarget target, String rawPassword, String ipAddress) {
    UUID tenantId = target.tenantId();
    UUID userId = target.userId();
    String contactValue = target.contactValue();

    // The caller may have run under another tenant (the signup link runs before any tenant
    // exists). Everything below reads and writes this tenant's rows.
    TenantContext.setCurrentTenantId(tenantId);
    tenantSessionBinder.bindToCurrentSession(tenantId);

    User userEntity = loadUser(tenantId, userId, contactValue);

    // The link proves e-mail ownership: make sure the user has one verified contact, so that a
    // login by any verified contact works and the token below can name one.
    if (userEntity.getAnyVerifiedContact().isEmpty()) {
      ensureAuthenticationContact(tenantId, userId, contactValue);
      // Detach the stale user entity and reload it with the fresh contact associations.
      entityManager.detach(userEntity);
      userEntity = loadUser(tenantId, userId, contactValue);
    }

    boolean identityExists = identityProvisioningService.isEmailRegistered(contactValue);
    String passwordHash = null;
    if (!identityExists) {
      if (rawPassword == null || rawPassword.isBlank()) {
        throw new PlatformDomainException("Password is required", "AUTH_PASSWORD_REQUIRED", 400);
      }
      passwordHash = passwordEncoder.encode(rawPassword);
    }

    LoginIdentity identity =
        identityProvisioningService.provisionFromVerifiedLink(
            contactValue, passwordHash, tenantId, userId);
    mirrorAuthUser(identity, tenantId, userId);
    log.info(
        "Setup link used: tenantId={}, userId={}, identityExisted={}",
        tenantId,
        userId,
        identityExists);

    if (identityExists) {
      eventPublisher.publish(new UserRegisteredEvent(tenantId, userId, contactValue));
      return LoginResponse.builder().needsOnboarding(false).signInRequired(true).build();
    }

    String accessToken = jwtService.generateAccessToken(userEntity);
    String refreshToken = jwtService.generateRefreshToken(userEntity);

    // Persist refresh token for token refresh flow
    RefreshToken refreshTokenEntity =
        RefreshToken.create(userId, refreshToken, Instant.now().plusMillis(refreshTokenExpiration));
    refreshTokenEntity.setTenantId(tenantId);
    refreshTokenRepository.save(refreshTokenEntity);

    String eventContact =
        userEntity
            .getAnyVerifiedContact()
            .map(contact -> contact.getContactValue())
            .orElse(contactValue);
    eventPublisher.publish(new UserRegisteredEvent(tenantId, userId, eventContact));
    // Auto-login event for audit trail (the link and password are a successful login)
    eventPublisher.publish(new UserLoginEvent(tenantId, userId, eventContact, ipAddress));

    UserDto freshUser =
        UserDto.from(
            userEntity,
            employeeProjectionPort.findByUserId(tenantId, userEntity.getId()).orElse(null));

    OnboardingPrefillDto onboardingPrefill =
        target.needsOnboarding() ? buildPrefill(target, eventContact) : null;

    log.info(
        "Account setup completed: tenantId={}, userId={}, needsOnboarding={}",
        tenantId,
        userId,
        target.needsOnboarding());

    return LoginResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .expiresIn(accessTokenExpiration / 1000) // Convert ms to seconds
        .user(freshUser)
        .needsOnboarding(target.needsOnboarding())
        .onboardingPrefill(onboardingPrefill)
        .build();
  }

  private User loadUser(UUID tenantId, UUID userId, String contactValue) {
    return userRepository
        .findByTenantIdAndContactValue(tenantId, contactValue)
        .orElseGet(
            () ->
                userRepository
                    .findByTenantIdAndId(tenantId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("User entity not found")));
  }

  /**
   * The user's own copy of the login (AuthUser) mirrors the identity: its hash and MFA settings are
   * the identity's, so an existing identity's password is never replaced by a new one.
   */
  private void mirrorAuthUser(LoginIdentity identity, UUID tenantId, UUID userId) {
    AuthUser authUser = AuthUser.create(userId, identity.getPasswordHash());
    authUser.setTenantId(tenantId);
    authUser.setIsMfaEnabled(Boolean.TRUE.equals(identity.getIsMfaEnabled()));
    authUser.setPrimaryMfaType(identity.getPrimaryMfaType());
    authUser.setMfaSecret(identity.getMfaSecret());
    authUser.verify();
    authUserRepository.save(authUser);
  }

  private OnboardingPrefillDto buildPrefill(SetupTarget target, String primaryEmail) {
    var orgOpt =
        organizationRepository.findByTenantIdAndId(target.tenantId(), target.organizationId());
    var prefill =
        OnboardingPrefillDto.builder()
            .primaryEmail(primaryEmail)
            .organizationName(orgOpt.map(Organization::getName).orElse(null))
            .legalName(
                orgOpt
                    .map(
                        o -> {
                          String legal = o.getLegalName();
                          return (legal != null && !legal.isBlank()) ? legal : o.getName();
                        })
                    .orElse(null))
            .taxId(orgOpt.map(Organization::getTaxId).orElse(null))
            .organizationType(orgOpt.map(o -> o.getOrganizationType().name()).orElse(null));

    // Sales-led: prefill address from Organization primary address; self-signup: no address
    organizationAddressRepository
        .findPrimaryWithAddressByOrganizationId(target.organizationId())
        .map(oa -> oa.getAddress())
        .ifPresent(
            addr ->
                prefill
                    .addressLine1(addr.getStreetAddress())
                    .addressLine2(addr.getAddressLine2())
                    .city(addr.getCity())
                    .state(addr.getState())
                    .district(addr.getDistrict())
                    .postalCode(addr.getPostalCode())
                    .country(addr.getCountry()));

    return prefill.build();
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
