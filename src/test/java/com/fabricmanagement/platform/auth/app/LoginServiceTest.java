package com.fabricmanagement.platform.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.persistence.TenantSessionBinder;
import com.fabricmanagement.platform.auth.domain.LoginIdentity;
import com.fabricmanagement.platform.auth.domain.Membership;
import com.fabricmanagement.platform.auth.domain.MembershipStatus;
import com.fabricmanagement.platform.auth.domain.MfaType;
import com.fabricmanagement.platform.auth.domain.RefreshToken;
import com.fabricmanagement.platform.auth.dto.LoginRequest;
import com.fabricmanagement.platform.auth.dto.LoginResponse;
import com.fabricmanagement.platform.auth.infra.repository.LoginIdentityRepository;
import com.fabricmanagement.platform.auth.infra.repository.MembershipRepository;
import com.fabricmanagement.platform.auth.infra.repository.RefreshTokenRepository;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.user.api.facade.UserFacade;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.UserContact;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

  @Mock private AuthUserResolutionService resolutionService;
  @Mock private LoginIdentityRepository loginIdentityRepository;
  @Mock private MembershipRepository membershipRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private UserFacade userFacade;
  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtService jwtService;
  @Mock private DomainEventPublisher eventPublisher;
  @Mock private VerificationCodeManager verificationCodeManager;
  @Mock private TotpMfaService totpMfaService;
  @Mock private TrustedDeviceService trustedDeviceService;
  @Mock private MfaRateLimitService mfaRateLimitService;
  @Mock private MfaEventService mfaEventService;
  @Mock private TenantSessionBinder tenantSessionBinder;

  @InjectMocks private LoginService loginService;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(loginService, "accessTokenExpiration", 900_000L);
    ReflectionTestUtils.setField(loginService, "refreshTokenExpiration", 604_800_000L);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void shouldLoginViaLoginIdentityWithSingleMembership() {
    UUID identityId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    LoginIdentity identity = identity(identityId, "admin@example.com", "hash");
    Membership membership = membership(identityId, tenantId, userId, true);
    User userEntity = userEntity(tenantId, userId, "admin@example.com");
    UserDto userDto = userDto(tenantId, userId);

    when(loginIdentityRepository.findByEmail("admin@example.com"))
        .thenReturn(Optional.of(identity));
    when(resolutionService.validate(identity)).thenReturn(AuthValidationResult.valid());
    when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
    when(membershipRepository.findByLoginIdentityIdAndStatus(identityId, MembershipStatus.ACTIVE))
        .thenReturn(List.of(membership));
    when(userFacade.findById(tenantId, userId)).thenReturn(Optional.of(userDto));
    when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(userEntity));
    when(jwtService.generateAccessToken(userEntity)).thenReturn("access-token");
    when(jwtService.generateRefreshToken(userEntity)).thenReturn("refresh-token");

    LoginResponse response =
        loginService.login(loginRequest("ADMIN@EXAMPLE.COM", "secret"), "127.0.0.1", "agent");

    assertThat(response.getAccessToken()).isEqualTo("access-token");
    assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
    assertThat(response.getUser()).isSameAs(userDto);
    verify(loginIdentityRepository).findByEmail("admin@example.com");
    verify(resolutionService).resetFailedAttempts(identity);
    verify(refreshTokenRepository).save(any(RefreshToken.class));
    verify(tenantSessionBinder).bindToCurrentSession(tenantId);
  }

  @Test
  void shouldRecordFailedAttemptAndLockIdentityOnWrongPassword() {
    UUID identityId = UUID.randomUUID();
    LoginIdentity identity = identity(identityId, "admin@example.com", "hash");

    when(loginIdentityRepository.findByEmail("admin@example.com"))
        .thenReturn(Optional.of(identity));
    when(passwordEncoder.matches("bad-password", "hash")).thenReturn(false);
    doAnswer(
            invocation -> {
              LoginIdentity failedIdentity = invocation.getArgument(0);
              failedIdentity.recordFailedLogin(1, 60);
              return null;
            })
        .when(resolutionService)
        .recordFailedAttempt(identity);

    assertThatThrownBy(
            () ->
                loginService.login(
                    loginRequest("admin@example.com", "bad-password"), "127.0.0.1", "agent"))
        .isInstanceOf(PlatformDomainException.class)
        .satisfies(
            exception ->
                assertThat(((PlatformDomainException) exception).getErrorCode())
                    .isEqualTo("AUTH_INVALID_CREDENTIALS"));

    assertThat(identity.isLocked()).isTrue();
    verify(resolutionService).recordFailedAttempt(identity);
  }

  @Test
  void shouldDenyLoginWhenIdentityRequiresPasswordReset() {
    LoginIdentity identity = identity(UUID.randomUUID(), "admin@example.com", "hash");
    identity.setRequiresPasswordReset(true);

    when(loginIdentityRepository.findByEmail("admin@example.com"))
        .thenReturn(Optional.of(identity));
    when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
    when(resolutionService.validate(identity))
        .thenReturn(AuthValidationResult.passwordResetRequired());

    assertThatThrownBy(
            () ->
                loginService.login(
                    loginRequest("admin@example.com", "secret"), "127.0.0.1", "agent"))
        .isInstanceOf(PlatformDomainException.class)
        .satisfies(
            exception ->
                assertThat(((PlatformDomainException) exception).getErrorCode())
                    .isEqualTo("AUTH_PASSWORD_RESET_REQUIRED"));
  }

  @Test
  void missingAccountAndWrongPasswordGiveTheSameResponse() {
    LoginIdentity identity = identity(UUID.randomUUID(), "admin@example.com", "hash");
    when(loginIdentityRepository.findByEmail("admin@example.com"))
        .thenReturn(Optional.of(identity));
    when(loginIdentityRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.matches("bad-password", "hash")).thenReturn(false);

    PlatformDomainException wrongPassword =
        loginFailure(loginRequest("admin@example.com", "bad-password"));
    PlatformDomainException missingAccount =
        loginFailure(loginRequest("nobody@example.com", "bad-password"));

    assertSameExternalResponse(missingAccount, wrongPassword);
    assertThat(missingAccount.getErrorCode()).isEqualTo("AUTH_INVALID_CREDENTIALS");
    assertThat(missingAccount.getHttpStatus()).isEqualTo(401);
  }

  @Test
  void missingAccountStillSpendsAPasswordCheck() {
    when(loginIdentityRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode(any())).thenReturn("placeholder-hash");

    loginFailure(loginRequest("nobody@example.com", "some-password"));

    verify(passwordEncoder).matches("some-password", "placeholder-hash");
  }

  @Test
  void missingAccountDoesNotLookUpOrganizationsByEmailDomain() {
    when(loginIdentityRepository.findByEmail("someone@acme.example")).thenReturn(Optional.empty());

    PlatformDomainException failure =
        loginFailure(loginRequest("someone@acme.example", "some-password"));

    assertThat(failure.getMessage()).isEqualTo("Invalid credentials");
    verifyNoInteractions(userRepository);
  }

  @Test
  void lockedAccountAnswersLikeAWrongPasswordEvenWithTheRightPassword() {
    LoginIdentity identity = identity(UUID.randomUUID(), "admin@example.com", "hash");
    identity.recordFailedLogin(1, 600);
    LoginIdentity other = identity(UUID.randomUUID(), "other@example.com", "other-hash");
    when(loginIdentityRepository.findByEmail("admin@example.com"))
        .thenReturn(Optional.of(identity));
    when(loginIdentityRepository.findByEmail("other@example.com")).thenReturn(Optional.of(other));
    when(passwordEncoder.matches("bad-password", "other-hash")).thenReturn(false);

    PlatformDomainException locked = loginFailure(loginRequest("admin@example.com", "secret"));
    PlatformDomainException wrongPassword =
        loginFailure(loginRequest("other@example.com", "bad-password"));

    assertSameExternalResponse(locked, wrongPassword);
    verify(passwordEncoder, never()).matches("secret", "hash");
    verify(resolutionService, never()).validate(identity);
  }

  @Test
  void accountStatusIsNotRevealedToAWrongPassword() {
    LoginIdentity identity = identity(UUID.randomUUID(), "admin@example.com", "hash");
    identity.setEmailVerified(false);
    when(loginIdentityRepository.findByEmail("admin@example.com"))
        .thenReturn(Optional.of(identity));
    when(passwordEncoder.matches("bad-password", "hash")).thenReturn(false);

    PlatformDomainException failure =
        loginFailure(loginRequest("admin@example.com", "bad-password"));

    assertThat(failure.getErrorCode()).isEqualTo("AUTH_INVALID_CREDENTIALS");
    verify(resolutionService, never()).validate(identity);
  }

  @Test
  void accountStatusIsReportedWithItsOwnCodeAfterTheRightPassword() {
    LoginIdentity identity = identity(UUID.randomUUID(), "admin@example.com", "hash");
    identity.setEmailVerified(false);
    when(loginIdentityRepository.findByEmail("admin@example.com"))
        .thenReturn(Optional.of(identity));
    when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
    when(resolutionService.validate(identity)).thenReturn(AuthValidationResult.notVerified());

    PlatformDomainException failure = loginFailure(loginRequest("admin@example.com", "secret"));

    assertThat(failure.getErrorCode()).isEqualTo("AUTH_ACCOUNT_NOT_VERIFIED");
    assertThat(failure.getHttpStatus()).isEqualTo(400);
  }

  @Test
  void shouldUseDefaultMembershipWhenIdentityHasMultipleActiveMemberships() {
    UUID identityId = UUID.randomUUID();
    UUID firstTenantId = UUID.randomUUID();
    UUID firstUserId = UUID.randomUUID();
    UUID defaultTenantId = UUID.randomUUID();
    UUID defaultUserId = UUID.randomUUID();
    LoginIdentity identity = identity(identityId, "admin@example.com", "hash");
    Membership firstMembership = membership(identityId, firstTenantId, firstUserId, false);
    Membership defaultMembership = membership(identityId, defaultTenantId, defaultUserId, true);
    User userEntity = userEntity(defaultTenantId, defaultUserId, "admin@example.com");
    UserDto userDto = userDto(defaultTenantId, defaultUserId);

    when(loginIdentityRepository.findByEmail("admin@example.com"))
        .thenReturn(Optional.of(identity));
    when(resolutionService.validate(identity)).thenReturn(AuthValidationResult.valid());
    when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
    when(membershipRepository.findByLoginIdentityIdAndStatus(identityId, MembershipStatus.ACTIVE))
        .thenReturn(List.of(firstMembership, defaultMembership));
    when(userFacade.findById(defaultTenantId, defaultUserId)).thenReturn(Optional.of(userDto));
    when(userRepository.findByTenantIdAndId(defaultTenantId, defaultUserId))
        .thenReturn(Optional.of(userEntity));
    when(jwtService.generateAccessToken(userEntity)).thenReturn("access-token");
    when(jwtService.generateRefreshToken(userEntity)).thenReturn("refresh-token");

    LoginResponse response =
        loginService.login(loginRequest("admin@example.com", "secret"), "127.0.0.1", "agent");

    assertThat(response.getUser()).isSameAs(userDto);
    verify(userFacade).findById(defaultTenantId, defaultUserId);
    verify(tenantSessionBinder).bindToCurrentSession(defaultTenantId);
  }

  private PlatformDomainException loginFailure(LoginRequest request) {
    Throwable thrown = catchThrowable(() -> loginService.login(request, "127.0.0.1", "agent"));
    assertThat(thrown).isInstanceOf(PlatformDomainException.class);
    return (PlatformDomainException) thrown;
  }

  private static void assertSameExternalResponse(
      PlatformDomainException actual, PlatformDomainException expected) {
    assertThat(actual.getErrorCode()).isEqualTo(expected.getErrorCode());
    assertThat(actual.getHttpStatus()).isEqualTo(expected.getHttpStatus());
    assertThat(actual.getMessage()).isEqualTo(expected.getMessage());
    assertThat(actual.getArgs()).isEqualTo(expected.getArgs());
  }

  private LoginRequest loginRequest(String email, String password) {
    return LoginRequest.builder().contactValue(email).password(password).build();
  }

  private LoginIdentity identity(UUID identityId, String email, String passwordHash) {
    return LoginIdentity.builder()
        .id(identityId)
        .email(email)
        .passwordHash(passwordHash)
        .isMfaEnabled(false)
        .primaryMfaType(MfaType.NONE)
        .isActive(true)
        .emailVerified(true)
        .failedLoginAttempts(0)
        .requiresPasswordReset(false)
        .build();
  }

  private Membership membership(UUID identityId, UUID tenantId, UUID userId, boolean isDefault) {
    return Membership.builder()
        .id(UUID.randomUUID())
        .loginIdentityId(identityId)
        .tenantId(tenantId)
        .userId(userId)
        .status(MembershipStatus.ACTIVE)
        .isDefault(isDefault)
        .build();
  }

  private UserDto userDto(UUID tenantId, UUID userId) {
    return UserDto.builder()
        .id(userId)
        .tenantId(tenantId)
        .uid("ACME-001-USER-00001")
        .organizationId(UUID.randomUUID())
        .isActive(true)
        .hasCompletedOnboarding(true)
        .build();
  }

  private User userEntity(UUID tenantId, UUID userId, String email) {
    Contact contact =
        Contact.builder()
            .contactType(ContactType.EMAIL)
            .contactValue(email)
            .isVerified(true)
            .build();
    User user = User.create("Admin", "User", UUID.randomUUID());
    user.setId(userId);
    user.setTenantId(tenantId);
    user.setUid("ACME-001-USER-00001");
    user.getUserContacts().add(UserContact.builder().contact(contact).isDefault(true).build());
    user.completeOnboarding();
    return user;
  }
}
