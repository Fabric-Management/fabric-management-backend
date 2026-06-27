package com.fabricmanagement.platform.auth.app.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tenant.api.facade.TenantFacade;
import com.fabricmanagement.platform.tenant.dto.CreateTenantRequest;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateTenantStepTest {

  private final TenantFacade tenantFacade = org.mockito.Mockito.mock(TenantFacade.class);
  private final CreateTenantStep step = new CreateTenantStep(tenantFacade);

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("self-service tenant creation defers trial activation until password setup")
  void shouldDeferTrialActivationForSelfServiceTenant() {
    UUID tenantId = UUID.randomUUID();
    OnboardingContext context = new OnboardingContext();
    context.setOrganizationName("Acme Textiles");
    context.setOrganizationEmail("billing@example.com");
    context.setCountry("TR");
    context.setTrialDays(90);
    context.setSalesLed(false);
    context.setDemoMode(true);
    when(tenantFacade.createTenant(org.mockito.ArgumentMatchers.any(CreateTenantRequest.class)))
        .thenReturn(TenantDto.builder().id(tenantId).uid("ACME-001").build());

    step.execute(context);

    ArgumentCaptor<CreateTenantRequest> requestCaptor =
        ArgumentCaptor.forClass(CreateTenantRequest.class);
    verify(tenantFacade).createTenant(requestCaptor.capture());
    assertThat(requestCaptor.getValue().getTrialDays()).isEqualTo(90);
    assertThat(requestCaptor.getValue().isDeferTrialActivation()).isTrue();
    assertThat(requestCaptor.getValue().isDemoMode()).isTrue();
    assertThat(context.getTenantId()).isEqualTo(tenantId);
  }

  @Test
  @DisplayName("sales-led tenant creation keeps demo mode disabled")
  void shouldDisableDemoModeForSalesLedTenant() {
    UUID tenantId = UUID.randomUUID();
    OnboardingContext context = new OnboardingContext();
    context.setOrganizationName("Acme Textiles");
    context.setOrganizationEmail("billing@example.com");
    context.setCountry("TR");
    context.setTrialDays(90);
    context.setSalesLed(true);
    context.setDemoMode(false);
    when(tenantFacade.createTenant(org.mockito.ArgumentMatchers.any(CreateTenantRequest.class)))
        .thenReturn(TenantDto.builder().id(tenantId).uid("ACME-001").build());

    step.execute(context);

    ArgumentCaptor<CreateTenantRequest> requestCaptor =
        ArgumentCaptor.forClass(CreateTenantRequest.class);
    verify(tenantFacade).createTenant(requestCaptor.capture());
    assertThat(requestCaptor.getValue().isDeferTrialActivation()).isFalse();
    assertThat(requestCaptor.getValue().isDemoMode()).isFalse();
  }
}
