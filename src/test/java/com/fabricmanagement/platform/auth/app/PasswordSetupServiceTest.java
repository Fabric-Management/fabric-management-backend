package com.fabricmanagement.platform.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.persistence.TenantSessionBinder;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.infrastructure.tenant.TenantReference;
import com.fabricmanagement.common.infrastructure.tenant.TrialLifecyclePort;
import com.fabricmanagement.platform.auth.domain.RegistrationToken;
import com.fabricmanagement.platform.auth.domain.RegistrationTokenType;
import com.fabricmanagement.platform.auth.dto.PasswordSetupRequest;
import com.fabricmanagement.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.platform.auth.infra.repository.RefreshTokenRepository;
import com.fabricmanagement.platform.auth.infra.repository.RegistrationTokenRepository;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.app.ContactService;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationAddressRepository;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.user.api.facade.UserFacade;
import com.fabricmanagement.platform.user.app.UserContactAssignmentService;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.UserContact;
import com.fabricmanagement.platform.user.domain.port.EmployeeProjectionPort;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.testsupport.SensitiveLogCapture;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PasswordSetupServiceTest {

  @Mock private RegistrationTokenRepository tokenRepository;
  @Mock private AuthUserRepository authUserRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private UserFacade userFacade;
  @Mock private UserRepository userRepository;
  @Mock private UserContactAssignmentService userContactAssignmentService;
  @Mock private ContactService contactService;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtService jwtService;
  @Mock private DomainEventPublisher eventPublisher;
  @Mock private EntityManager entityManager;
  @Mock private OrganizationRepository organizationRepository;
  @Mock private OrganizationAddressRepository organizationAddressRepository;
  @Mock private TenantQueryPort tenantQueryPort;
  @Mock private EmployeeProjectionPort employeeProjectionPort;
  @Mock private TrialLifecyclePort trialLifecyclePort;
  @Mock private TenantSessionBinder tenantSessionBinder;
  @Mock private IdentityProvisioningService identityProvisioningService;

  private PasswordSetupService service;

  @BeforeEach
  void setUp() {
    service =
        new PasswordSetupService(
            tokenRepository,
            authUserRepository,
            refreshTokenRepository,
            userFacade,
            userRepository,
            userContactAssignmentService,
            contactService,
            passwordEncoder,
            jwtService,
            eventPublisher,
            entityManager,
            organizationRepository,
            organizationAddressRepository,
            tenantQueryPort,
            employeeProjectionPort,
            trialLifecyclePort,
            tenantSessionBinder,
            identityProvisioningService);
    ReflectionTestUtils.setField(service, "refreshTokenExpiration", 604_800_000L);
    ReflectionTestUtils.setField(service, "accessTokenExpiration", 900_000L);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("self-service password setup activates the tenant trial")
  void shouldActivateSelfServiceTrialOnPasswordSetup() {
    PasswordSetupFixture fixture = arrangePasswordSetup(RegistrationTokenType.SELF_SERVICE);

    service.setupPassword(fixture.request(), "127.0.0.1");

    verify(trialLifecyclePort).startSelfServiceTrialIfNeeded(fixture.tenantId());
  }

  @Test
  @DisplayName("sales-led password setup leaves trial activation unchanged")
  void shouldNotActivateSalesLedTrialOnPasswordSetup() {
    PasswordSetupFixture fixture = arrangePasswordSetup(RegistrationTokenType.SALES_LED);

    service.setupPassword(fixture.request(), "127.0.0.1");

    verify(trialLifecyclePort, never()).startSelfServiceTrialIfNeeded(any());
  }

  @Test
  @DisplayName("password setup provisions platform identity and membership")
  void shouldProvisionIdentityAndMembershipOnPasswordSetup() {
    PasswordSetupFixture fixture = arrangePasswordSetup(RegistrationTokenType.SALES_LED);

    service.setupPassword(fixture.request(), "127.0.0.1");

    verify(identityProvisioningService)
        .provisionCredential(
            "admin@example.com",
            "hash",
            false,
            com.fabricmanagement.platform.auth.domain.MfaType.NONE,
            null,
            true,
            fixture.tenantId(),
            fixture.userId());
  }

  @Test
  void successfulSetupLogsOnlySafeIdentifiersAndStillReturnsSessionTokens() {
    PasswordSetupFixture fixture = arrangePasswordSetup(RegistrationTokenType.SELF_SERVICE);
    try (var logs = new SensitiveLogCapture(PasswordSetupService.class)) {
      var response = service.setupPassword(fixture.request(), "127.0.0.1");
      assertThat(response.getAccessToken()).isEqualTo("access-token");
      assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
      assertThat(logs.text()).contains(fixture.tenantId().toString(), fixture.userId().toString());
      assertThat(logs.text())
          .doesNotContain(
              fixture.request().getToken(),
              fixture.request().getToken().substring(0, 8),
              fixture.request().getPassword(),
              "access-token",
              "refresh-token");
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"expired", "used", "short"})
  void invalidTokensStayRejectedWithoutLoggingOrSubstringFailures(String state) {
    UUID tenantId = UUID.randomUUID();
    RegistrationToken token =
        RegistrationToken.create("eleanor.hughes@example.com", RegistrationTokenType.SELF_SERVICE);
    token.setToken(state.equals("short") ? "tiny" : "b24788a1-62fe-40ad-899f-80ea7543a1cc");
    if (state.equals("used")) token.markAsUsed();
    else token.setExpiresAt(Instant.now().minusSeconds(60));
    when(tenantQueryPort.findTenantIdByRegistrationToken(token.getToken()))
        .thenReturn(Optional.of(tenantId));
    when(tokenRepository.findByToken(token.getToken())).thenReturn(Optional.of(token));
    var request =
        PasswordSetupRequest.builder()
            .token(token.getToken())
            .password("Hawthorn!Secret27")
            .build();
    try (var logs = new SensitiveLogCapture(PasswordSetupService.class)) {
      assertThatThrownBy(() -> service.setupPassword(request, "127.0.0.1"))
          .isInstanceOfSatisfying(
              PlatformDomainException.class,
              failure -> assertThat(failure.getErrorCode()).isEqualTo("AUTH_TOKEN_INVALID"));
      assertThat(logs.text()).contains("AUTH_TOKEN_INVALID", tenantId.toString());
      assertThat(logs.text()).doesNotContain(request.getToken(), request.getPassword());
      if (request.getToken().length() >= 8) {
        assertThat(logs.text()).doesNotContain(request.getToken().substring(0, 8));
      }
      verify(passwordEncoder, never()).encode(any());
      verify(tokenRepository, never()).save(any());
    }
  }

  private PasswordSetupFixture arrangePasswordSetup(RegistrationTokenType tokenType) {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID organizationId = UUID.randomUUID();
    String contactValue = "admin@example.com";
    RegistrationToken token = RegistrationToken.create(contactValue, tokenType);
    PasswordSetupRequest request =
        PasswordSetupRequest.builder().token(token.getToken()).password("Str0ngPassword!").build();
    UserDto userDto =
        UserDto.builder()
            .id(userId)
            .tenantId(tenantId)
            .organizationId(organizationId)
            .hasCompletedOnboarding(true)
            .build();
    User user = buildVerifiedUser(tenantId, userId, organizationId, contactValue);

    when(tenantQueryPort.findTenantIdByRegistrationToken(token.getToken()))
        .thenReturn(Optional.of(tenantId));
    when(tokenRepository.findByToken(token.getToken())).thenReturn(Optional.of(token));
    when(userFacade.findByContactValue(contactValue)).thenReturn(Optional.of(userDto));
    when(authUserRepository.existsByUserId(userId)).thenReturn(false);
    when(tenantQueryPort.findById(tenantId))
        .thenReturn(
            Optional.of(
                new TenantReference(
                    tenantId, "HWN-001", "Hawthorn Weaving Ltd", "hawthorn-weaving")));
    when(userRepository.findByTenantIdAndContactValue(tenantId, contactValue))
        .thenReturn(Optional.of(user));
    when(passwordEncoder.encode(request.getPassword())).thenReturn("hash");
    when(jwtService.generateAccessToken(user)).thenReturn("access-token");
    when(jwtService.generateRefreshToken(user)).thenReturn("refresh-token");
    when(employeeProjectionPort.findByUserId(tenantId, userId)).thenReturn(Optional.empty());

    return new PasswordSetupFixture(request, tenantId, userId);
  }

  private static User buildVerifiedUser(
      UUID tenantId, UUID userId, UUID organizationId, String contactValue) {
    Contact contact =
        Contact.builder()
            .contactType(ContactType.EMAIL)
            .contactValue(contactValue)
            .isVerified(true)
            .build();
    User user = User.create("Eleanor", "Hughes", organizationId);
    user.setId(userId);
    user.setTenantId(tenantId);
    user.setUid("HWN-001-USER-00001");
    user.completeOnboarding();
    user.getUserContacts().add(UserContact.builder().contact(contact).isDefault(true).build());
    return user;
  }

  private record PasswordSetupFixture(PasswordSetupRequest request, UUID tenantId, UUID userId) {}
}
