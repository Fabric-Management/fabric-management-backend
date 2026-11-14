package com.fabricmanagement.platform.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.persistence.TenantSessionBinder;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.infrastructure.tenant.TenantReference;
import com.fabricmanagement.common.infrastructure.tenant.TrialLifecyclePort;
import com.fabricmanagement.platform.auth.app.AccountSetupService.SetupTarget;
import com.fabricmanagement.platform.auth.domain.RegistrationToken;
import com.fabricmanagement.platform.auth.domain.RegistrationTokenType;
import com.fabricmanagement.platform.auth.dto.LoginResponse;
import com.fabricmanagement.platform.auth.dto.PasswordSetupRequest;
import com.fabricmanagement.platform.auth.dto.SetupLinkStatus;
import com.fabricmanagement.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.platform.auth.infra.repository.RegistrationTokenRepository;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.user.api.facade.UserFacade;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.testsupport.SensitiveLogCapture;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Setup links of users that already exist (sales-led and invited). The self-service signup link is
 * {@link SignupCompletionServiceTest}; the login that ends both is {@link AccountSetupServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class PasswordSetupServiceTest {

  @Mock private RegistrationTokenRepository tokenRepository;
  @Mock private AuthUserRepository authUserRepository;
  @Mock private UserFacade userFacade;
  @Mock private TenantQueryPort tenantQueryPort;
  @Mock private TrialLifecyclePort trialLifecyclePort;
  @Mock private TenantSessionBinder tenantSessionBinder;
  @Mock private IdentityProvisioningService identityProvisioningService;
  @Mock private SignupCompletionService signupCompletion;
  @Mock private AccountSetupService accountSetup;

  private PasswordSetupService service;

  @BeforeEach
  void setUp() {
    service =
        new PasswordSetupService(
            tokenRepository,
            authUserRepository,
            userFacade,
            tenantQueryPort,
            trialLifecyclePort,
            tenantSessionBinder,
            identityProvisioningService,
            signupCompletion,
            accountSetup);
    // Most links are not a signup link: the signup path answers "not mine" and the legacy path
    // runs.
    lenient().when(signupCompletion.complete(any(), any(), any())).thenReturn(Optional.empty());
    lenient().when(signupCompletion.checkLink(any())).thenReturn(Optional.empty());
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("a legacy self-service token still activates the tenant trial")
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
  @DisplayName("password setup hands the login to the shared account setup and returns its result")
  void shouldFinishTheSetupThroughTheSharedAccountSetup() {
    PasswordSetupFixture fixture = arrangePasswordSetup(RegistrationTokenType.SALES_LED);

    LoginResponse response = service.setupPassword(fixture.request(), "127.0.0.1");

    assertThat(response).isSameAs(fixture.response());
    var target = ArgumentCaptor.forClass(SetupTarget.class);
    verify(accountSetup).finishSetup(target.capture(), eq("Str0ngPassword!"), eq("127.0.0.1"));
    assertThat(target.getValue().tenantId()).isEqualTo(fixture.tenantId());
    assertThat(target.getValue().userId()).isEqualTo(fixture.userId());
    assertThat(target.getValue().organizationId()).isEqualTo(fixture.organizationId());
    assertThat(target.getValue().contactValue()).isEqualTo("admin@example.com");
    assertThat(target.getValue().needsOnboarding()).isFalse();
  }

  @Test
  @DisplayName("a user who has not finished onboarding is sent to it after the setup")
  void shouldFlagOnboardingForAUserWhoHasNotCompletedIt() {
    PasswordSetupFixture fixture =
        arrangePasswordSetup(RegistrationTokenType.SALES_LED, /* onboardingDone */ false);

    service.setupPassword(fixture.request(), "127.0.0.1");

    var target = ArgumentCaptor.forClass(SetupTarget.class);
    verify(accountSetup).finishSetup(target.capture(), any(), any());
    assertThat(target.getValue().needsOnboarding()).isTrue();
  }

  @Test
  @DisplayName("the token is used up by the setup")
  void shouldMarkTheTokenAsUsed() {
    PasswordSetupFixture fixture = arrangePasswordSetup(RegistrationTokenType.SALES_LED);

    service.setupPassword(fixture.request(), "127.0.0.1");

    assertThat(fixture.token().isValid()).isFalse();
    verify(tokenRepository).save(fixture.token());
  }

  @Test
  @DisplayName("a signup link is completed by the signup path and never reaches the legacy path")
  void aSignupLinkIsCompletedBySignupCompletion() {
    LoginResponse completed = LoginResponse.builder().accessToken("access-token").build();
    var request =
        PasswordSetupRequest.builder()
            .token("signup-link-token")
            .password("Hawthorn!Secret27")
            .build();
    when(signupCompletion.complete("signup-link-token", "Hawthorn!Secret27", "127.0.0.1"))
        .thenReturn(Optional.of(completed));

    assertThat(service.setupPassword(request, "127.0.0.1")).isSameAs(completed);

    verifyNoInteractions(tenantQueryPort, tokenRepository, accountSetup, tenantSessionBinder);
  }

  @Test
  @DisplayName("success logs only safe identifiers")
  void successfulSetupLogsOnlySafeIdentifiers() {
    PasswordSetupFixture fixture = arrangePasswordSetup(RegistrationTokenType.SELF_SERVICE);
    try (var logs = new SensitiveLogCapture(PasswordSetupService.class)) {
      service.setupPassword(fixture.request(), "127.0.0.1");
      assertThat(logs.text()).contains(fixture.tenantId().toString());
      assertThat(logs.text())
          .doesNotContain(
              fixture.request().getToken(),
              fixture.request().getToken().substring(0, 8),
              fixture.request().getPassword());
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
      verify(accountSetup, never()).finishSetup(any(), any(), any());
      verify(tokenRepository, never()).save(any());
    }
  }

  @Test
  void anUnknownTokenIsAnInvalidTokenNotABareIllegalArgument() {
    // A link from a database that no longer exists, or a mistyped one: no tenant owns the token.
    String unknown = "1c08bc0a-1177-4c67-85ad-eae26b5939bb";
    when(tenantQueryPort.findTenantIdByRegistrationToken(unknown)).thenReturn(Optional.empty());
    var request =
        PasswordSetupRequest.builder().token(unknown).password("Hawthorn!Secret27").build();

    assertThatThrownBy(() -> service.setupPassword(request, "127.0.0.1"))
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            failure -> assertThat(failure.getErrorCode()).isEqualTo("AUTH_TOKEN_INVALID"));

    verify(tenantSessionBinder, never()).bindToCurrentSession(any());
    verify(accountSetup, never()).finishSetup(any(), any(), any());
    verify(tokenRepository, never()).save(any());
  }

  @Test
  void aUserWhoAlreadyHasALoginCannotUseTheLinkAgain() {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    RegistrationToken token =
        RegistrationToken.create("admin@example.com", RegistrationTokenType.SALES_LED);
    when(tenantQueryPort.findTenantIdByRegistrationToken(token.getToken()))
        .thenReturn(Optional.of(tenantId));
    when(tokenRepository.findByToken(token.getToken())).thenReturn(Optional.of(token));
    when(userFacade.findByContactValue("admin@example.com"))
        .thenReturn(Optional.of(UserDto.builder().id(userId).tenantId(tenantId).build()));
    when(authUserRepository.existsByUserId(userId)).thenReturn(true);
    var request =
        PasswordSetupRequest.builder().token(token.getToken()).password("Str0ngPassword!").build();

    assertThatThrownBy(() -> service.setupPassword(request, "127.0.0.1"))
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            failure -> assertThat(failure.getErrorCode()).isEqualTo("AUTH_PASSWORD_ALREADY_SET"));

    verify(accountSetup, never()).finishSetup(any(), any(), any());
    verify(tokenRepository, never()).save(any());
  }

  // ---- link check -------------------------------------------------------------------------

  @Test
  @DisplayName("checking a signup link answers from the signup path")
  void checkLinkAnswersForASignupLink() {
    when(signupCompletion.checkLink("signup-link-token"))
        .thenReturn(Optional.of(new SetupLinkStatus(true, true)));

    assertThat(service.checkLink("signup-link-token")).isEqualTo(new SetupLinkStatus(true, true));

    verifyNoInteractions(tenantQueryPort, tokenRepository);
  }

  @Test
  @DisplayName("a valid legacy token asks for a password only when the address has no login yet")
  void checkLinkAsksForAPasswordOnlyWhenNoLoginExists() {
    UUID tenantId = UUID.randomUUID();
    RegistrationToken token =
        RegistrationToken.create("admin@example.com", RegistrationTokenType.SALES_LED);
    when(tenantQueryPort.findTenantIdByRegistrationToken(token.getToken()))
        .thenReturn(Optional.of(tenantId));
    when(tokenRepository.findByToken(token.getToken())).thenReturn(Optional.of(token));
    when(identityProvisioningService.isEmailRegistered("admin@example.com"))
        .thenReturn(false)
        .thenReturn(true);

    assertThat(service.checkLink(token.getToken())).isEqualTo(new SetupLinkStatus(true, true));
    assertThat(service.checkLink(token.getToken())).isEqualTo(new SetupLinkStatus(true, false));
    verify(tenantSessionBinder, org.mockito.Mockito.times(2)).bindToCurrentSession(tenantId);
  }

  @Test
  @DisplayName("a legacy link whose user already has a password is reported as not usable")
  void checkLinkAgreesWithSetupWhenThePasswordIsAlreadySet() {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    RegistrationToken token =
        RegistrationToken.create("admin@example.com", RegistrationTokenType.SALES_LED);
    when(tenantQueryPort.findTenantIdByRegistrationToken(token.getToken()))
        .thenReturn(Optional.of(tenantId));
    when(tokenRepository.findByToken(token.getToken())).thenReturn(Optional.of(token));
    when(userFacade.findByContactValue("admin@example.com"))
        .thenReturn(Optional.of(UserDto.builder().id(userId).tenantId(tenantId).build()));
    when(authUserRepository.existsByUserId(userId)).thenReturn(true);

    assertThat(service.checkLink(token.getToken())).isEqualTo(SetupLinkStatus.invalid());
    verify(identityProvisioningService, never()).isEmailRegistered(any());
  }

  @Test
  @DisplayName("an unknown, expired or used link gets one and the same answer")
  void checkLinkAnswersInvalidForEveryUnusableLink() {
    UUID tenantId = UUID.randomUUID();
    RegistrationToken used =
        RegistrationToken.create("a@example.com", RegistrationTokenType.SALES_LED);
    used.markAsUsed();
    RegistrationToken expired =
        RegistrationToken.create("b@example.com", RegistrationTokenType.SALES_LED);
    expired.setExpiresAt(Instant.now().minusSeconds(60));
    when(tenantQueryPort.findTenantIdByRegistrationToken("unknown")).thenReturn(Optional.empty());
    when(tenantQueryPort.findTenantIdByRegistrationToken(used.getToken()))
        .thenReturn(Optional.of(tenantId));
    when(tenantQueryPort.findTenantIdByRegistrationToken(expired.getToken()))
        .thenReturn(Optional.of(tenantId));
    when(tokenRepository.findByToken(used.getToken())).thenReturn(Optional.of(used));
    when(tokenRepository.findByToken(expired.getToken())).thenReturn(Optional.of(expired));

    assertThat(service.checkLink("unknown")).isEqualTo(SetupLinkStatus.invalid());
    assertThat(service.checkLink(used.getToken())).isEqualTo(SetupLinkStatus.invalid());
    assertThat(service.checkLink(expired.getToken())).isEqualTo(SetupLinkStatus.invalid());
    verify(tokenRepository, never()).save(any());
  }

  // ---- fixtures ---------------------------------------------------------------------------

  private PasswordSetupFixture arrangePasswordSetup(RegistrationTokenType tokenType) {
    return arrangePasswordSetup(tokenType, true);
  }

  private PasswordSetupFixture arrangePasswordSetup(
      RegistrationTokenType tokenType, boolean onboardingDone) {
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
            .hasCompletedOnboarding(onboardingDone)
            .build();
    LoginResponse response =
        LoginResponse.builder().accessToken("access-token").refreshToken("refresh-token").build();

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
    when(accountSetup.finishSetup(any(), any(), any())).thenReturn(response);

    return new PasswordSetupFixture(request, tenantId, userId, organizationId, token, response);
  }

  private record PasswordSetupFixture(
      PasswordSetupRequest request,
      UUID tenantId,
      UUID userId,
      UUID organizationId,
      RegistrationToken token,
      LoginResponse response) {}
}
