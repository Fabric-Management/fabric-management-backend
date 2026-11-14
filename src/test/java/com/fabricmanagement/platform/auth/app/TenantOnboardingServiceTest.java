package com.fabricmanagement.platform.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.platform.auth.app.onboarding.OnboardingContext;
import com.fabricmanagement.platform.auth.app.onboarding.TenantOnboardingOrchestrator;
import com.fabricmanagement.platform.auth.domain.PendingSignup;
import com.fabricmanagement.platform.auth.domain.PendingSignupRequest;
import com.fabricmanagement.platform.auth.dto.TenantOnboardingRequest;
import com.fabricmanagement.platform.auth.dto.TenantOnboardingResponse;
import com.fabricmanagement.platform.organization.api.facade.OrganizationFacade;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class TenantOnboardingServiceTest {

  private final TenantOnboardingOrchestrator orchestrator =
      Mockito.mock(TenantOnboardingOrchestrator.class);
  private final OrganizationFacade organizationFacade = Mockito.mock(OrganizationFacade.class);
  private final IdentityProvisioningService identityProvisioningService =
      Mockito.mock(IdentityProvisioningService.class);
  private final TenantQueryPort tenantQueryPort = Mockito.mock(TenantQueryPort.class);
  private final TenantOnboardingService service =
      new TenantOnboardingService(
          orchestrator, organizationFacade, identityProvisioningService, tenantQueryPort);

  @Test
  void shouldMarkPlaygroundSelfServiceTenantAsDemoMode() {
    PendingSignup signup = pendingSignup("PLAYGROUND");
    when(orchestrator.onboard(Mockito.any(OnboardingContext.class)))
        .thenReturn(TenantOnboardingResponse.builder().build());

    service.createVerifiedSelfServiceTenant(signup);

    OnboardingContext context = onboardedContext();
    assertThat(context.isSalesLed()).isFalse();
    assertThat(context.isDemoMode()).isTrue();
    assertThat(context.getSignupIntent()).isEqualTo("PLAYGROUND");
  }

  @Test
  void shouldTreatAnUnknownStoredIntentAsPlayground() {
    when(orchestrator.onboard(Mockito.any(OnboardingContext.class)))
        .thenReturn(TenantOnboardingResponse.builder().build());

    service.createVerifiedSelfServiceTenant(pendingSignup("SOMETHING_ELSE"));

    OnboardingContext context = onboardedContext();
    assertThat(context.isDemoMode()).isTrue();
    assertThat(context.getSignupIntent()).isEqualTo("PLAYGROUND");
  }

  @Test
  void shouldCreateTrialSelfServiceTenantOutsideDemoMode() {
    when(orchestrator.onboard(Mockito.any(OnboardingContext.class)))
        .thenReturn(TenantOnboardingResponse.builder().build());

    service.createVerifiedSelfServiceTenant(pendingSignup("TRIAL"));

    OnboardingContext context = onboardedContext();
    assertThat(context.isSalesLed()).isFalse();
    assertThat(context.isDemoMode()).isFalse();
    assertThat(context.getSignupIntent()).isEqualTo("TRIAL");
  }

  @Test
  void shouldBuildTheCompanyFromTheStoredSignupAndKeepItsEvidenceIdentity() {
    PendingSignup signup = pendingSignup("PLAYGROUND");
    when(orchestrator.onboard(Mockito.any(OnboardingContext.class)))
        .thenReturn(TenantOnboardingResponse.builder().build());

    service.createVerifiedSelfServiceTenant(signup);

    OnboardingContext context = onboardedContext();
    // The lead recorded when the form was sent is found by the pending signup's id.
    assertThat(context.getSignupId()).isEqualTo(signup.getId());
    assertThat(context.getTermsAcceptedAt()).isEqualTo(signup.getTermsAcceptedAt());
    assertThat(context.getOrganizationName()).isEqualTo("Acme Textiles");
    assertThat(context.getTaxId()).isEqualTo("ACME-123");
    assertThat(context.getAdminContact()).isEqualTo("owner@example.com");
    assertThat(context.getOrganizationEmail()).isEqualTo("owner@example.com");
    assertThat(context.getAdminFirstName()).isEqualTo("Oliver");
    assertThat(context.getSelectedOS()).containsExactly("FabricOS");
  }

  @Test
  void shouldNotMailAnInvitationOrIssueATokenForTheVerifiedSignup() {
    when(orchestrator.onboard(Mockito.any(OnboardingContext.class)))
        .thenReturn(TenantOnboardingResponse.builder().build());

    service.createVerifiedSelfServiceTenant(pendingSignup("PLAYGROUND"));

    // The admin's login is created in the same transaction as the company.
    assertThat(onboardedContext().isInvitationEmailSuppressed()).isTrue();
    assertThat(onboardedContext().isExistingIdentity()).isFalse();
  }

  @Test
  void shouldKeepSalesLedTenantOutOfDemoMode() {
    TenantOnboardingRequest request =
        TenantOnboardingRequest.builder()
            .organizationName("Enterprise Textiles")
            .taxId("ENT-123")
            .organizationType(OrganizationType.VERTICAL_MILL)
            .organizationEmail("billing@example.com")
            .adminFirstName("Sales")
            .adminLastName("Owner")
            .adminContact("owner@example.com")
            .build();
    when(orchestrator.onboard(Mockito.any(OnboardingContext.class)))
        .thenReturn(TenantOnboardingResponse.builder().build());

    service.createSalesLedTenant(request);

    ArgumentCaptor<OnboardingContext> contextCaptor =
        ArgumentCaptor.forClass(OnboardingContext.class);
    verify(orchestrator).onboard(contextCaptor.capture());
    assertThat(contextCaptor.getValue().isSalesLed()).isTrue();
    assertThat(contextCaptor.getValue().isDemoMode()).isFalse();
    assertThat(contextCaptor.getValue().getSignupIntent()).isEqualTo("SALES_LED");
  }

  private OnboardingContext onboardedContext() {
    ArgumentCaptor<OnboardingContext> contextCaptor =
        ArgumentCaptor.forClass(OnboardingContext.class);
    verify(orchestrator).onboard(contextCaptor.capture());
    return contextCaptor.getValue();
  }

  /** The entity is read-only (no constructor, no setters): build one the way Hibernate does. */
  private static PendingSignup pendingSignup(String intent) {
    PendingSignup signup = BeanUtils.instantiateClass(PendingSignup.class);
    ReflectionTestUtils.setField(signup, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(signup, "email", "owner@example.com");
    ReflectionTestUtils.setField(signup, "taxId", "ACME-123");
    ReflectionTestUtils.setField(signup, "termsAcceptedAt", Instant.parse("2026-11-14T08:00:00Z"));
    ReflectionTestUtils.setField(
        signup,
        "request",
        new PendingSignupRequest(
            "Acme Textiles",
            OrganizationType.VERTICAL_MILL,
            "Oliver",
            "Owner",
            List.of("FabricOS"),
            intent));
    return signup;
  }
}
