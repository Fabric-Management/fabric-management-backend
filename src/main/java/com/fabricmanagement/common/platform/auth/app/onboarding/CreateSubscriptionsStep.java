package com.fabricmanagement.common.platform.auth.app.onboarding;

import com.fabricmanagement.common.platform.company.api.facade.CompanyFacade;
import com.fabricmanagement.common.platform.company.dto.CreateInitialSubscriptionsResult;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(4)
@Component
@RequiredArgsConstructor
@Slf4j
public class CreateSubscriptionsStep implements OnboardingStep {

  private final CompanyFacade companyFacade;

  @Override
  public void execute(OnboardingContext context) {
    UUID tenantId = context.getTenantId();
    if (tenantId == null) {
      return;
    }
    List<String> selectedOS = context.getSelectedOS();
    int trialDays = context.isSalesLed() ? context.getTrialDays() : 14;
    if (selectedOS == null || selectedOS.isEmpty()) {
      selectedOS = List.of("FabricOS");
    }
    CreateInitialSubscriptionsResult result =
        companyFacade.createInitialSubscriptions(tenantId, selectedOS, trialDays);
    context.setSubscriptionOsCodes(result.getOsCodes());
    context.setTrialEndsAt(result.getTrialEndsAt());
    log.debug("CreateSubscriptionsStep: osCodes={}", result.getOsCodes());
  }
}
