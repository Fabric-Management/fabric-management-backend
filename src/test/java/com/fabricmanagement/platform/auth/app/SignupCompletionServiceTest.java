package com.fabricmanagement.platform.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.persistence.TenantSessionBinder;
import com.fabricmanagement.common.infrastructure.tenant.TrialLifecyclePort;
import com.fabricmanagement.common.infrastructure.web.exception.TaxIdAlreadyExistsException;
import com.fabricmanagement.platform.auth.app.AccountSetupService.SetupTarget;
import com.fabricmanagement.platform.auth.domain.PendingSignup;
import com.fabricmanagement.platform.auth.dto.LoginResponse;
import com.fabricmanagement.platform.auth.dto.SetupLinkStatus;
import com.fabricmanagement.platform.auth.dto.TenantOnboardingResponse;
import com.fabricmanagement.platform.auth.infra.repository.PendingSignupRepository;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.organization.api.facade.OrganizationFacade;
import com.fabricmanagement.testsupport.SensitiveLogCapture;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SignupCompletionServiceTest {

  private static final String RAW_TOKEN = "Zk3pQ9-raw-signup-link-token-7Fh2";
  private static final String PASSWORD = "Hawthorn!Secret27";
  private static final String EMAIL = "eleanor.hughes@example.com";

  @Mock private PendingSignupRepository pendingSignups;
  @Mock private TenantOnboardingService onboarding;
  @Mock private AccountSetupService accountSetup;
  @Mock private IdentityProvisioningService identities;
  @Mock private OrganizationFacade organizationFacade;
  @Mock private TrialLifecyclePort trialLifecyclePort;
  @Mock private TenantSessionBinder tenantSessionBinder;

  private SignupCompletionService service;

  private final UUID pendingId = UUID.randomUUID();
  private final UUID tenantId = UUID.randomUUID();
  private final UUID adminUserId = UUID.randomUUID();
  private final UUID organizationId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service =
        new SignupCompletionService(
            pendingSignups,
            onboarding,
            accountSetup,
            identities,
            organizationFacade,
            trialLifecyclePort,
            tenantSessionBinder);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  // ---- complete ---------------------------------------------------------------------------

  @Test
  @DisplayName("a token that belongs to no pending signup is not ours: empty, nothing touched")
  void anUnknownTokenIsLeftToTheLegacyPath() {
    when(pendingSignups.findLockedByTokenHash(SignupLinkToken.hash("unknown")))
        .thenReturn(Optional.empty());

    assertThat(service.complete("unknown", PASSWORD, "127.0.0.1")).isEmpty();

    verifyNoInteractions(
        onboarding, accountSetup, organizationFacade, trialLifecyclePort, tenantSessionBinder);
  }

  @Test
  @DisplayName("an expired link is refused and builds nothing")
  void anExpiredLinkIsRefused() {
    arrangePending(Instant.now().minusSeconds(60), null);

    assertThatThrownBy(() -> service.complete(RAW_TOKEN, PASSWORD, "127.0.0.1"))
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            failure -> {
              assertThat(failure.getErrorCode()).isEqualTo("AUTH_TOKEN_INVALID");
              assertThat(failure.getHttpStatus()).isEqualTo(400);
            });

    verifyNothingBuilt();
  }

  @Test
  @DisplayName("a link that was already used is refused like any other unusable link")
  void aUsedLinkIsRefused() {
    arrangePending(Instant.now().plus(1, ChronoUnit.HOURS), Instant.now().minusSeconds(30));

    assertThatThrownBy(() -> service.complete(RAW_TOKEN, PASSWORD, "127.0.0.1"))
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            failure -> assertThat(failure.getErrorCode()).isEqualTo("AUTH_TOKEN_INVALID"));

    verifyNothingBuilt();
  }

  @Test
  @DisplayName("a tax id registered since the form was sent stops the completion")
  void aTaxIdTakenInTheMeantimeStopsTheBuild() {
    arrangePending(Instant.now().plus(1, ChronoUnit.HOURS), null);
    when(organizationFacade.existsByTaxId("GB123456789")).thenReturn(true);

    assertThatThrownBy(() -> service.complete(RAW_TOKEN, PASSWORD, "127.0.0.1"))
        .isInstanceOf(TaxIdAlreadyExistsException.class);

    verify(onboarding, never()).createVerifiedSelfServiceTenant(any());
    verify(pendingSignups, never()).markCompleted(any(), any(), any());
    verify(accountSetup, never()).finishSetup(any(), any(), any());
  }

  @Test
  @DisplayName("a good link builds the company, starts the trial, closes the signup and signs in")
  void aGoodLinkBuildsTheCompanyAndFinishesTheSetup() {
    PendingSignup pending = arrangePending(Instant.now().plus(1, ChronoUnit.HOURS), null);
    LoginResponse response = LoginResponse.builder().accessToken("access-token").build();
    arrangeTenantBuild(pending);
    when(accountSetup.finishSetup(any(), any(), any())).thenReturn(response);

    Optional<LoginResponse> result = service.complete(RAW_TOKEN, PASSWORD, "127.0.0.1");

    assertThat(result).containsSame(response);
    assertThat(TenantContext.getCurrentTenantIdOrNull()).isEqualTo(tenantId);

    InOrder order =
        inOrder(
            tenantSessionBinder,
            organizationFacade,
            onboarding,
            trialLifecyclePort,
            pendingSignups,
            accountSetup);
    order.verify(tenantSessionBinder).bindToCurrentSession(TenantContext.SYSTEM_TENANT_ID);
    order.verify(organizationFacade).existsByTaxId("GB123456789");
    order.verify(onboarding).createVerifiedSelfServiceTenant(pending);
    order.verify(tenantSessionBinder).bindToCurrentSession(tenantId);
    order.verify(trialLifecyclePort).startSelfServiceTrialIfNeeded(tenantId);
    order.verify(pendingSignups).markCompleted(eq(pendingId), eq(tenantId), any(Instant.class));
    var target = ArgumentCaptor.forClass(SetupTarget.class);
    order.verify(accountSetup).finishSetup(target.capture(), eq(PASSWORD), eq("127.0.0.1"));

    assertThat(target.getValue().tenantId()).isEqualTo(tenantId);
    assertThat(target.getValue().userId()).isEqualTo(adminUserId);
    assertThat(target.getValue().organizationId()).isEqualTo(organizationId);
    assertThat(target.getValue().contactValue()).isEqualTo(EMAIL);
    assertThat(target.getValue().needsOnboarding()).isTrue();
  }

  @Test
  @DisplayName("a failing onboarding step leaves the signup open: it is not marked completed")
  void aFailingBuildDoesNotCloseTheSignup() {
    PendingSignup pending = arrangePending(Instant.now().plus(1, ChronoUnit.HOURS), null);
    when(organizationFacade.existsByTaxId("GB123456789")).thenReturn(false);
    when(onboarding.createVerifiedSelfServiceTenant(pending))
        .thenThrow(new IllegalStateException("step failed"));

    assertThatThrownBy(() -> service.complete(RAW_TOKEN, PASSWORD, "127.0.0.1"))
        .isInstanceOf(IllegalStateException.class);

    verify(trialLifecyclePort, never()).startSelfServiceTrialIfNeeded(any());
    verify(pendingSignups, never()).markCompleted(any(), any(), any());
    verify(accountSetup, never()).finishSetup(any(), any(), any());
  }

  @Test
  @DisplayName("a failing login setup propagates, so the whole transaction rolls back")
  void aFailingAccountSetupPropagates() {
    PendingSignup pending = arrangePending(Instant.now().plus(1, ChronoUnit.HOURS), null);
    arrangeTenantBuild(pending);
    when(accountSetup.finishSetup(any(), any(), any()))
        .thenThrow(
            new PlatformDomainException("Password is required", "AUTH_PASSWORD_REQUIRED", 400));

    assertThatThrownBy(() -> service.complete(RAW_TOKEN, null, "127.0.0.1"))
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            failure -> assertThat(failure.getErrorCode()).isEqualTo("AUTH_PASSWORD_REQUIRED"));
  }

  @Test
  @DisplayName("logs carry ids only, never the link, the password or the e-mail address")
  void completionLogsOnlySafeIdentifiers() {
    PendingSignup pending = arrangePending(Instant.now().plus(1, ChronoUnit.HOURS), null);
    arrangeTenantBuild(pending);
    when(accountSetup.finishSetup(any(), any(), any()))
        .thenReturn(LoginResponse.builder().accessToken("access-token").build());

    try (var logs = new SensitiveLogCapture(SignupCompletionService.class)) {
      service.complete(RAW_TOKEN, PASSWORD, "127.0.0.1");

      assertThat(logs.text()).contains(pendingId.toString(), tenantId.toString());
      assertThat(logs.text())
          .doesNotContain(
              RAW_TOKEN,
              RAW_TOKEN.substring(0, 8),
              SignupLinkToken.hash(RAW_TOKEN),
              PASSWORD,
              EMAIL);
    }
  }

  @Test
  @DisplayName("refusals log the pending signup id and the reason, nothing sensitive")
  void refusalsLogOnlySafeIdentifiers() {
    arrangePending(Instant.now().minusSeconds(60), null);

    try (var logs = new SensitiveLogCapture(SignupCompletionService.class)) {
      assertThatThrownBy(() -> service.complete(RAW_TOKEN, PASSWORD, "127.0.0.1"))
          .isInstanceOf(PlatformDomainException.class);

      assertThat(logs.text()).contains(pendingId.toString(), "AUTH_TOKEN_INVALID");
      assertThat(logs.text())
          .doesNotContain(RAW_TOKEN, SignupLinkToken.hash(RAW_TOKEN), PASSWORD, EMAIL);
    }
  }

  // ---- checkLink --------------------------------------------------------------------------

  @Test
  @DisplayName("checking a link that is no signup link answers empty")
  void checkLinkIsEmptyForAnUnknownToken() {
    when(pendingSignups.findByTokenHash(SignupLinkToken.hash("unknown")))
        .thenReturn(Optional.empty());

    assertThat(service.checkLink("unknown")).isEmpty();
  }

  @Test
  @DisplayName("a usable link asks for a password when the address has no login yet")
  void checkLinkAsksForAPasswordForANewAddress() {
    arrangePendingForCheck(Instant.now().plus(1, ChronoUnit.HOURS), null);
    when(identities.isEmailRegistered(EMAIL)).thenReturn(false);

    assertThat(service.checkLink(RAW_TOKEN)).contains(new SetupLinkStatus(true, true));
  }

  @Test
  @DisplayName("a usable link asks for no password when the address already has a login")
  void checkLinkAsksForNoPasswordWhenALoginExists() {
    arrangePendingForCheck(Instant.now().plus(1, ChronoUnit.HOURS), null);
    when(identities.isEmailRegistered(EMAIL)).thenReturn(true);

    assertThat(service.checkLink(RAW_TOKEN)).contains(new SetupLinkStatus(true, false));
  }

  @Test
  @DisplayName("an expired or used link is reported invalid, and checking never uses the link")
  void checkLinkReportsUnusableLinksAsInvalid() {
    PendingSignup expired = arrangePendingForCheck(Instant.now().minusSeconds(60), null);

    assertThat(service.checkLink(RAW_TOKEN)).contains(SetupLinkStatus.invalid());

    ReflectionTestUtils.setField(expired, "expiresAt", Instant.now().plus(1, ChronoUnit.HOURS));
    ReflectionTestUtils.setField(expired, "completedAt", Instant.now().minusSeconds(30));

    assertThat(service.checkLink(RAW_TOKEN)).contains(SetupLinkStatus.invalid());

    verify(pendingSignups, never()).findLockedByTokenHash(any());
    verify(pendingSignups, never()).markCompleted(any(), any(), any());
    verifyNoInteractions(identities, onboarding, accountSetup);
  }

  // ---- fixtures ---------------------------------------------------------------------------

  private PendingSignup pending(Instant expiresAt, Instant completedAt) {
    PendingSignup pending = BeanUtils.instantiateClass(PendingSignup.class);
    ReflectionTestUtils.setField(pending, "id", pendingId);
    ReflectionTestUtils.setField(pending, "email", EMAIL);
    ReflectionTestUtils.setField(pending, "taxId", "GB123456789");
    ReflectionTestUtils.setField(pending, "tokenHash", SignupLinkToken.hash(RAW_TOKEN));
    ReflectionTestUtils.setField(pending, "termsAcceptedAt", Instant.now().minusSeconds(120));
    ReflectionTestUtils.setField(pending, "expiresAt", expiresAt);
    ReflectionTestUtils.setField(pending, "completedAt", completedAt);
    return pending;
  }

  private PendingSignup arrangePending(Instant expiresAt, Instant completedAt) {
    PendingSignup pending = pending(expiresAt, completedAt);
    when(pendingSignups.findLockedByTokenHash(SignupLinkToken.hash(RAW_TOKEN)))
        .thenReturn(Optional.of(pending));
    return pending;
  }

  private PendingSignup arrangePendingForCheck(Instant expiresAt, Instant completedAt) {
    PendingSignup pending = pending(expiresAt, completedAt);
    when(pendingSignups.findByTokenHash(SignupLinkToken.hash(RAW_TOKEN)))
        .thenReturn(Optional.of(pending));
    return pending;
  }

  private void arrangeTenantBuild(PendingSignup pending) {
    when(organizationFacade.existsByTaxId("GB123456789")).thenReturn(false);
    when(onboarding.createVerifiedSelfServiceTenant(pending))
        .thenReturn(
            TenantOnboardingResponse.builder()
                .tenantId(tenantId)
                .adminUserId(adminUserId)
                .organizationId(organizationId)
                .build());
  }

  private void verifyNothingBuilt() {
    verifyNoInteractions(
        onboarding, accountSetup, organizationFacade, trialLifecyclePort, tenantSessionBinder);
    verify(pendingSignups, never()).markCompleted(any(), any(), any());
  }
}
