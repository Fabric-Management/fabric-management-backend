package com.fabricmanagement.platform.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.persistence.TenantSessionBinder;
import com.fabricmanagement.platform.auth.app.AccountSetupService.SetupTarget;
import com.fabricmanagement.platform.auth.domain.AuthUser;
import com.fabricmanagement.platform.auth.domain.LoginIdentity;
import com.fabricmanagement.platform.auth.domain.MfaType;
import com.fabricmanagement.platform.auth.domain.RefreshToken;
import com.fabricmanagement.platform.auth.domain.event.UserLoginEvent;
import com.fabricmanagement.platform.auth.domain.event.UserRegisteredEvent;
import com.fabricmanagement.platform.auth.dto.LoginResponse;
import com.fabricmanagement.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.platform.auth.infra.repository.RefreshTokenRepository;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.app.ContactService;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationAddressRepository;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.user.app.UserContactAssignmentService;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.UserContact;
import com.fabricmanagement.platform.user.domain.port.EmployeeProjectionPort;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.testsupport.SensitiveLogCapture;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AccountSetupServiceTest {

  private static final String CONTACT = "admin@example.com";
  private static final String PASSWORD = "Str0ngPassword!";

  @Mock private AuthUserRepository authUserRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private UserRepository userRepository;
  @Mock private UserContactAssignmentService userContactAssignmentService;
  @Mock private ContactService contactService;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtService jwtService;
  @Mock private DomainEventPublisher eventPublisher;
  @Mock private EntityManager entityManager;
  @Mock private OrganizationRepository organizationRepository;
  @Mock private OrganizationAddressRepository organizationAddressRepository;
  @Mock private EmployeeProjectionPort employeeProjectionPort;
  @Mock private TenantSessionBinder tenantSessionBinder;
  @Mock private IdentityProvisioningService identityProvisioningService;

  private AccountSetupService service;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final UUID organizationId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new AccountSetupService(
            authUserRepository,
            refreshTokenRepository,
            userRepository,
            userContactAssignmentService,
            contactService,
            passwordEncoder,
            jwtService,
            eventPublisher,
            entityManager,
            organizationRepository,
            organizationAddressRepository,
            employeeProjectionPort,
            tenantSessionBinder,
            identityProvisioningService);
    ReflectionTestUtils.setField(service, "refreshTokenExpiration", 604_800_000L);
    ReflectionTestUtils.setField(service, "accessTokenExpiration", 900_000L);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  // ---- no login yet: the person chooses a password and is signed in -----------------------

  @Test
  @DisplayName("an address without a login gets one, with the chosen password, and is signed in")
  void aNewIdentityGetsTheChosenPasswordAndASession() {
    User user = verifiedUser();
    arrangeUser(user);
    when(identityProvisioningService.isEmailRegistered(CONTACT)).thenReturn(false);
    when(passwordEncoder.encode(PASSWORD)).thenReturn("hash");
    when(identityProvisioningService.provisionFromVerifiedLink(CONTACT, "hash", tenantId, userId))
        .thenReturn(identity("hash"));
    arrangeTokens(user);

    LoginResponse response = service.finishSetup(target(false), PASSWORD, "127.0.0.1");

    assertThat(response.getAccessToken()).isEqualTo("access-token");
    assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
    assertThat(response.getExpiresIn()).isEqualTo(900L);
    assertThat(response.getSignInRequired()).isNull();
    assertThat(response.getNeedsOnboarding()).isFalse();
    assertThat(response.getOnboardingPrefill()).isNull();
    assertThat(response.getUser()).isNotNull();
    verify(tenantSessionBinder).bindToCurrentSession(tenantId);

    var authUser = ArgumentCaptor.forClass(AuthUser.class);
    verify(authUserRepository).save(authUser.capture());
    assertThat(authUser.getValue().getUserId()).isEqualTo(userId);
    assertThat(authUser.getValue().getTenantId()).isEqualTo(tenantId);
    assertThat(authUser.getValue().getPasswordHash()).isEqualTo("hash");

    var refresh = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokenRepository).save(refresh.capture());
    assertThat(refresh.getValue().getUserId()).isEqualTo(userId);
    assertThat(refresh.getValue().getToken()).isEqualTo("refresh-token");
    assertThat(refresh.getValue().getTenantId()).isEqualTo(tenantId);

    var events = ArgumentCaptor.forClass(DomainEvent.class);
    verify(eventPublisher, times(2)).publish(events.capture());
    assertThat(events.getAllValues())
        .extracting(event -> event.getClass().getSimpleName())
        .containsExactly("UserRegisteredEvent", "UserLoginEvent");
  }

  @Test
  @DisplayName("a person who still has to fill in the company profile gets it prefilled")
  void aNewCompanyAdminGetsThePrefillWhenOnboardingIsStillOpen() {
    User user = verifiedUser();
    arrangeUser(user);
    when(identityProvisioningService.isEmailRegistered(CONTACT)).thenReturn(false);
    when(passwordEncoder.encode(PASSWORD)).thenReturn("hash");
    when(identityProvisioningService.provisionFromVerifiedLink(CONTACT, "hash", tenantId, userId))
        .thenReturn(identity("hash"));
    arrangeTokens(user);

    LoginResponse response = service.finishSetup(target(true), PASSWORD, "127.0.0.1");

    assertThat(response.getNeedsOnboarding()).isTrue();
    assertThat(response.getOnboardingPrefill()).isNotNull();
    assertThat(response.getOnboardingPrefill().getPrimaryEmail()).isEqualTo(CONTACT);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   "})
  @DisplayName("an address without a login cannot be set up without a password")
  void aNewIdentityNeedsAPassword(String missing) {
    arrangeUser(verifiedUser());
    when(identityProvisioningService.isEmailRegistered(CONTACT)).thenReturn(false);

    assertThatThrownBy(() -> service.finishSetup(target(false), missing, "127.0.0.1"))
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            failure -> assertThat(failure.getErrorCode()).isEqualTo("AUTH_PASSWORD_REQUIRED"));

    verify(passwordEncoder, never()).encode(any());
    verify(identityProvisioningService, never())
        .provisionFromVerifiedLink(any(), any(), any(), any());
    verify(authUserRepository, never()).save(any());
    verify(refreshTokenRepository, never()).save(any());
  }

  // ---- the address already has a login: add the company, do not sign in -------------------

  @Test
  @DisplayName("an existing login keeps its password and the person signs in normally")
  void anExistingIdentityIsNotSignedInAndKeepsItsPassword() {
    arrangeUser(verifiedUser());
    when(identityProvisioningService.isEmailRegistered(CONTACT)).thenReturn(true);
    when(identityProvisioningService.provisionFromVerifiedLink(CONTACT, null, tenantId, userId))
        .thenReturn(identity("old-hash"));

    LoginResponse response = service.finishSetup(target(true), PASSWORD, "127.0.0.1");

    assertThat(response.getSignInRequired()).isTrue();
    assertThat(response.getNeedsOnboarding()).isFalse();
    assertThat(response.getAccessToken()).isNull();
    assertThat(response.getRefreshToken()).isNull();
    assertThat(response.getUser()).isNull();
    assertThat(response.getOnboardingPrefill()).isNull();

    // The password typed on the link is not used: the existing login's password stays.
    verify(passwordEncoder, never()).encode(any());
    var authUser = ArgumentCaptor.forClass(AuthUser.class);
    verify(authUserRepository).save(authUser.capture());
    assertThat(authUser.getValue().getPasswordHash()).isEqualTo("old-hash");

    // No session: no tokens, no refresh token row, no login event.
    verify(jwtService, never()).generateAccessToken(any(User.class));
    verify(jwtService, never()).generateAccessToken(any(UserDto.class));
    verify(jwtService, never()).generateRefreshToken(any(User.class));
    verify(jwtService, never()).generateRefreshToken(any(UserDto.class));
    verify(refreshTokenRepository, never()).save(any());
    verify(eventPublisher).publish(any(UserRegisteredEvent.class));
    verify(eventPublisher, never()).publish(any(UserLoginEvent.class));
  }

  @Test
  @DisplayName("an existing login needs no password on the link")
  void anExistingIdentityNeedsNoPassword() {
    arrangeUser(verifiedUser());
    when(identityProvisioningService.isEmailRegistered(CONTACT)).thenReturn(true);
    when(identityProvisioningService.provisionFromVerifiedLink(CONTACT, null, tenantId, userId))
        .thenReturn(identity("old-hash"));

    LoginResponse response = service.finishSetup(target(false), null, "127.0.0.1");

    assertThat(response.getSignInRequired()).isTrue();
  }

  // ---- recovery, lookup and logging ------------------------------------------------------

  @Test
  @DisplayName(
      "a user without a verified contact gets one from the link, then the user is reloaded")
  void aMissingVerifiedContactIsRecoveredFromTheLink() {
    UUID contactId = UUID.randomUUID();
    Contact contact =
        Contact.builder()
            .contactType(ContactType.EMAIL)
            .contactValue(CONTACT)
            .isVerified(false)
            .build();
    contact.setId(contactId);
    User stale = newUser();
    User reloaded = verifiedUser();
    when(userRepository.findByTenantIdAndContactValue(tenantId, CONTACT))
        .thenReturn(Optional.of(stale))
        .thenReturn(Optional.of(reloaded));
    when(contactService.findByValue(CONTACT)).thenReturn(Optional.empty());
    when(contactService.createContact(any(), any(), any())).thenReturn(contact);
    when(identityProvisioningService.isEmailRegistered(CONTACT)).thenReturn(false);
    when(passwordEncoder.encode(PASSWORD)).thenReturn("hash");
    when(identityProvisioningService.provisionFromVerifiedLink(CONTACT, "hash", tenantId, userId))
        .thenReturn(identity("hash"));
    arrangeTokens(reloaded);

    LoginResponse response = service.finishSetup(target(false), PASSWORD, "127.0.0.1");

    verify(contactService).verifyContact(contactId);
    verify(userContactAssignmentService).assignContactForProvisioning(userId, contactId, true);
    verify(entityManager).detach(stale);
    // The tokens are made for the reloaded user, the one that has the verified contact.
    verify(jwtService).generateAccessToken(reloaded);
    assertThat(response.getAccessToken()).isEqualTo("access-token");
  }

  @Test
  @DisplayName("the user is found by id when the contact lookup does not find it")
  void theUserIsFoundByIdWhenTheContactLookupMisses() {
    User user = verifiedUser();
    when(userRepository.findByTenantIdAndContactValue(tenantId, CONTACT))
        .thenReturn(Optional.empty());
    when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(user));
    when(identityProvisioningService.isEmailRegistered(CONTACT)).thenReturn(true);
    when(identityProvisioningService.provisionFromVerifiedLink(CONTACT, null, tenantId, userId))
        .thenReturn(identity("old-hash"));

    assertThat(service.finishSetup(target(false), null, "127.0.0.1").getSignInRequired()).isTrue();
  }

  @Test
  @DisplayName("success logs only safe identifiers")
  void successfulSetupLogsOnlySafeIdentifiers() {
    User user = verifiedUser();
    arrangeUser(user);
    when(identityProvisioningService.isEmailRegistered(CONTACT)).thenReturn(false);
    when(passwordEncoder.encode(PASSWORD)).thenReturn("hash-9f3a71");
    when(identityProvisioningService.provisionFromVerifiedLink(
            CONTACT, "hash-9f3a71", tenantId, userId))
        .thenReturn(identity("hash-9f3a71"));
    arrangeTokens(user);

    try (var logs = new SensitiveLogCapture(AccountSetupService.class)) {
      service.finishSetup(target(false), PASSWORD, "127.0.0.1");

      assertThat(logs.text()).contains(tenantId.toString(), userId.toString());
      assertThat(logs.text())
          .doesNotContain(PASSWORD, "hash-9f3a71", "access-token", "refresh-token", CONTACT);
    }
  }

  // ---- fixtures ---------------------------------------------------------------------------

  private SetupTarget target(boolean needsOnboarding) {
    return new SetupTarget(tenantId, userId, organizationId, CONTACT, needsOnboarding);
  }

  private void arrangeUser(User user) {
    when(userRepository.findByTenantIdAndContactValue(tenantId, CONTACT))
        .thenReturn(Optional.of(user));
  }

  private void arrangeTokens(User user) {
    when(jwtService.generateAccessToken(user)).thenReturn("access-token");
    when(jwtService.generateRefreshToken(user)).thenReturn("refresh-token");
    when(employeeProjectionPort.findByUserId(tenantId, userId)).thenReturn(Optional.empty());
  }

  private LoginIdentity identity(String passwordHash) {
    return LoginIdentity.builder()
        .id(UUID.randomUUID())
        .email(CONTACT)
        .passwordHash(passwordHash)
        .isMfaEnabled(false)
        .primaryMfaType(MfaType.NONE)
        .isActive(true)
        .emailVerified(true)
        .failedLoginAttempts(0)
        .requiresPasswordReset(false)
        .build();
  }

  private User newUser() {
    User user = User.create("Eleanor", "Hughes", organizationId);
    user.setId(userId);
    user.setTenantId(tenantId);
    user.setUid("HWN-001-USER-00001");
    return user;
  }

  private User verifiedUser() {
    Contact contact =
        Contact.builder()
            .contactType(ContactType.EMAIL)
            .contactValue(CONTACT)
            .isVerified(true)
            .build();
    User user = newUser();
    user.completeOnboarding();
    user.getUserContacts().add(UserContact.builder().contact(contact).isDefault(true).build());
    return user;
  }
}
