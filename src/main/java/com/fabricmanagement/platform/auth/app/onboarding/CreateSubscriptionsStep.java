package com.fabricmanagement.platform.auth.app.onboarding;

import com.fabricmanagement.platform.subscription.app.SubscriptionService;
import com.fabricmanagement.platform.subscription.dto.CreateInitialSubscriptionsResult;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Step 5: Create initial OS subscriptions for the tenant.
 *
 * <p>Uses SubscriptionService directly for subscription management.
 */
@Order(7) // After AssignContactAndAddressStep (6)
@Component
@RequiredArgsConstructor
@Slf4j
public class CreateSubscriptionsStep implements OnboardingStep {

  private final SubscriptionService subscriptionService;

  @Override
  public void execute(OnboardingContext context) {
    UUID tenantId = context.getTenantId();
    if (tenantId == null) {
      return;
    }
    List<String> selectedOS = context.getSelectedOS();
    int trialDays = context.getTrialDays();
    if (selectedOS == null || selectedOS.isEmpty()) {
      selectedOS = List.of("FabricOS");
    }
    CreateInitialSubscriptionsResult result =
        subscriptionService.createInitialSubscriptions(tenantId, selectedOS, trialDays);
    context.setSubscriptionOsCodes(result.getOsCodes());
    context.setTrialEndsAt(result.getTrialEndsAt());
    log.debug("CreateSubscriptionsStep: osCodes={}", result.getOsCodes());
  }
}
