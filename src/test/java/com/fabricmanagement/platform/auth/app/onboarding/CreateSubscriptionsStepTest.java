package com.fabricmanagement.platform.auth.app.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
  private final CreateSubscriptionsStep step = new CreateSubscriptionsStep(subscriptionService);

  @Test
  @DisplayName("uses the onboarding trial window for self-service subscriptions")
  void shouldUseContextTrialDaysForSelfServiceSubscriptions() {
    UUID tenantId = UUID.randomUUID();
    Instant trialEndsAt = Instant.parse("2026-09-25T00:00:00Z");
    OnboardingContext context = new OnboardingContext();
    context.setTenantId(tenantId);
    context.setSalesLed(false);
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
}
