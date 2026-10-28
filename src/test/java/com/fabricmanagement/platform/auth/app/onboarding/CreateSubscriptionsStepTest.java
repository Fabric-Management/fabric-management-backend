package com.fabricmanagement.platform.auth.app.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.platform.subscription.app.EvaluationSubscriptionService;
import com.fabricmanagement.platform.subscription.app.SubscriptionService;
import com.fabricmanagement.platform.subscription.dto.CreateInitialSubscriptionsResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CreateSubscriptionsStepTest {

  private final SubscriptionService subscriptionService =
      org.mockito.Mockito.mock(SubscriptionService.class);
  private final EvaluationSubscriptionService evaluations =
      org.mockito.Mockito.mock(EvaluationSubscriptionService.class);
  private final CreateSubscriptionsStep step =
      new CreateSubscriptionsStep(subscriptionService, evaluations);

  @Test
  @DisplayName("uses the onboarding trial window for self-service subscriptions")
  void shouldUseContextTrialDaysForSelfServiceSubscriptions() {
    UUID tenantId = UUID.randomUUID();
    Instant trialEndsAt = Instant.parse("2026-09-25T00:00:00Z");
    OnboardingContext context = new OnboardingContext();
    context.setTenantId(tenantId);
    context.setSalesLed(false);
    context.setSignupIntent("TRIAL");
    context.setTrialDays(90);
    context.setSelectedOS(List.of("FabricOS"));
    when(subscriptionService.createInitialSubscriptions(
            eq(tenantId), eq(List.of("FabricOS")), eq(90)))
        .thenReturn(
            CreateInitialSubscriptionsResult.builder()
                .osCodes(List.of("FabricOS"))
                .trialEndsAt(trialEndsAt)
                .build());

    step.execute(context);

    verify(subscriptionService).createInitialSubscriptions(tenantId, List.of("FabricOS"), 90);
    assertThat(context.getTrialEndsAt()).isEqualTo(trialEndsAt);
    assertThat(context.getSubscriptionOsCodes()).containsExactly("FabricOS");
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"OMITTED", "EMPTY", "LEGACY_BASE_ONLY"})
  void playgroundAllocationIsServerOwnedEvenForOlderClients(String input) {
    var context = new OnboardingContext();
    context.setTenantId(UUID.randomUUID());
    context.setSignupIntent("PLAYGROUND");
    context.setDemoMode(true);
    context.setSelectedOS(
        input.equals("OMITTED") ? null : input.equals("EMPTY") ? List.of() : List.of("FabricOS"));
    when(evaluations.initialisePlayground(context.getTenantId()))
        .thenReturn(
            CreateInitialSubscriptionsResult.builder()
                .osCodes(List.of("FabricOS", "WarehouseOS"))
                .build());
    step.execute(context);
    assertThat(context.getSelectedOS()).containsExactly("FabricOS", "WarehouseOS");
    assertThat(context.getSubscriptionOsCodes()).containsExactly("FabricOS", "WarehouseOS");
    assertThat(context.getTrialEndsAt()).isNull();
    org.mockito.Mockito.verifyNoInteractions(subscriptionService);
  }

  @Test
  void salesLedKeepsItsExplicitPackageAndWindowPolicy() {
    var context = new OnboardingContext();
    context.setTenantId(UUID.randomUUID());
    context.setSignupIntent("SALES_LED");
    context.setSalesLed(true);
    context.setSelectedOS(List.of("FabricOS", "YarnOS"));
    context.setTrialDays(30);
    when(subscriptionService.createInitialSubscriptions(
            context.getTenantId(), context.getSelectedOS(), 30))
        .thenReturn(
            CreateInitialSubscriptionsResult.builder().osCodes(context.getSelectedOS()).build());
    step.execute(context);
    verify(subscriptionService)
        .createInitialSubscriptions(context.getTenantId(), List.of("FabricOS", "YarnOS"), 30);
    org.mockito.Mockito.verifyNoInteractions(evaluations);
  }
}
