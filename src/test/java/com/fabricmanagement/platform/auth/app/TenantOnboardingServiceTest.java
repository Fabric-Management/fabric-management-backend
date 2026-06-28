package com.fabricmanagement.platform.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.platform.auth.app.onboarding.OnboardingContext;
import com.fabricmanagement.platform.auth.app.onboarding.TenantOnboardingOrchestrator;
import com.fabricmanagement.platform.auth.dto.SelfSignupRequest;
import com.fabricmanagement.platform.auth.dto.SignupIntent;
import com.fabricmanagement.platform.auth.dto.TenantOnboardingRequest;
import com.fabricmanagement.platform.auth.dto.TenantOnboardingResponse;
import com.fabricmanagement.platform.organization.api.facade.OrganizationFacade;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.user.api.facade.UserFacade;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class TenantOnboardingServiceTest {

  private final TenantOnboardingOrchestrator orchestrator =
      Mockito.mock(TenantOnboardingOrchestrator.class);
  private final OrganizationFacade organizationFacade = Mockito.mock(OrganizationFacade.class);
  private final UserFacade userFacade = Mockito.mock(UserFacade.class);
  private final TenantOnboardingService service =
      new TenantOnboardingService(orchestrator, organizationFacade, userFacade);

  @Test
  void shouldMarkPlaygroundSelfServiceTenantAsDemoMode() {
    SelfSignupRequest request = selfSignupRequest(SignupIntent.PLAYGROUND);
    when(orchestrator.onboard(Mockito.any(OnboardingContext.class)))
        .thenReturn(TenantOnboardingResponse.builder().build());

    service.createSelfServiceTenant(request);

    ArgumentCaptor<OnboardingContext> contextCaptor =
        ArgumentCaptor.forClass(OnboardingContext.class);
    verify(orchestrator).onboard(contextCaptor.capture());
    assertThat(contextCaptor.getValue().isSalesLed()).isFalse();
    assertThat(contextCaptor.getValue().isDemoMode()).isTrue();
  }

  @Test
  void shouldDefaultSelfServiceTenantToDemoModeWhenIntentIsNull() {
    SelfSignupRequest request = selfSignupRequest(SignupIntent.PLAYGROUND);
    request.setIntent(null);
    when(orchestrator.onboard(Mockito.any(OnboardingContext.class)))
        .thenReturn(TenantOnboardingResponse.builder().build());

    service.createSelfServiceTenant(request);

    ArgumentCaptor<OnboardingContext> contextCaptor =
        ArgumentCaptor.forClass(OnboardingContext.class);
    verify(orchestrator).onboard(contextCaptor.capture());
    assertThat(contextCaptor.getValue().isSalesLed()).isFalse();
    assertThat(contextCaptor.getValue().isDemoMode()).isTrue();
  }

  @Test
  void shouldCreateTrialSelfServiceTenantOutsideDemoMode() {
    SelfSignupRequest request = selfSignupRequest(SignupIntent.TRIAL);
    when(orchestrator.onboard(Mockito.any(OnboardingContext.class)))
        .thenReturn(TenantOnboardingResponse.builder().build());

    service.createSelfServiceTenant(request);

    ArgumentCaptor<OnboardingContext> contextCaptor =
        ArgumentCaptor.forClass(OnboardingContext.class);
    verify(orchestrator).onboard(contextCaptor.capture());
    assertThat(contextCaptor.getValue().isSalesLed()).isFalse();
    assertThat(contextCaptor.getValue().isDemoMode()).isFalse();
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
  }

  private SelfSignupRequest selfSignupRequest(SignupIntent intent) {
    SelfSignupRequest.SelfSignupRequestBuilder builder =
        SelfSignupRequest.builder()
            .organizationName("Acme Textiles")
            .taxId("ACME-123")
            .organizationType(OrganizationType.VERTICAL_MILL)
            .firstName("Fatih")
            .lastName("Owner")
            .email("owner@example.com")
            .acceptedTerms(true);
    if (intent != null) {
      builder.intent(intent);
    }
    return builder.build();
  }
}
