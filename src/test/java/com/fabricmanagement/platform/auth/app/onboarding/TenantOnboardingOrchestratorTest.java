package com.fabricmanagement.platform.auth.app.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.fabricmanagement.platform.auth.dto.TenantOnboardingResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantOnboardingOrchestrator")
class TenantOnboardingOrchestratorTest {

  @Mock private OnboardingStep step1;
  @Mock private OnboardingStep step2;

  private TenantOnboardingOrchestrator orchestrator;
  private SimpleMeterRegistry meterRegistry;

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    orchestrator = new TenantOnboardingOrchestrator(List.of(step1, step2), meterRegistry);
  }

  @Test
  @DisplayName("onboard runs all steps in order and returns context toResult")
  void onboardRunsStepsAndReturnsResult() {
    OnboardingContext context = new OnboardingContext();
    context.setOrganizationName("Test Co");
    context.setOrganizationId(UUID.randomUUID());
    context.setTenantId(UUID.randomUUID());
    context.setUserId(UUID.randomUUID());
    context.setAdminContactValue("admin@test.com");
    context.setRegistrationToken("tok");
    context.setSetupUrl("https://app/setup?token=tok");
    context.setSubscriptionOsCodes(List.of("FabricOS"));

    TenantOnboardingResponse result = orchestrator.onboard(context);

    ArgumentCaptor<OnboardingContext> captor = ArgumentCaptor.forClass(OnboardingContext.class);
    verify(step1).execute(captor.capture());
    assertThat(captor.getValue()).isSameAs(context);
    verify(step2).execute(context);
    assertThat(result.getOrganizationId()).isEqualTo(context.getOrganizationId());
    assertThat(result.getAdminContactValue()).isEqualTo("admin@test.com");
    assertThat(result.getRegistrationToken()).isEqualTo("tok");
  }
}
