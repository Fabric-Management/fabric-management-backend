package com.fabricmanagement.platform.auth.app.onboarding;

import com.fabricmanagement.common.infrastructure.config.FrontendUrlProvider;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.web.LocalizationContext;
import com.fabricmanagement.platform.auth.domain.event.SelfSignupCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(12) // After permission clone and registered-demo seed; email send happens after commit.
@Component
@RequiredArgsConstructor
@Slf4j
public class PublishSelfSignupCompletedStep implements OnboardingStep {

  private final FrontendUrlProvider frontendUrlProvider;
  private final DomainEventPublisher eventPublisher;

  @Override
  public void execute(OnboardingContext context) {
    if (context.isExistingIdentity()) {
      log.debug(
          "PublishSelfSignupCompletedStep: skipping existing identity tenantId={}",
          context.getTenantId());
      return;
    }

    String token = context.getRegistrationToken();
    String email = context.getAdminContact();
    if (token == null || email == null) {
      log.warn(
          "PublishSelfSignupCompletedStep: missing token or recipient email, skipping signup email event. "
              + "tenantId={}, userId={}, tokenPresent={}, emailPresent={}",
          context.getTenantId(),
          context.getUserId(),
          token != null,
          email != null);
      return;
    }

    String setupUrl = frontendUrlProvider.buildUrl("/setup?token=" + token);
    context.setSetupUrl(setupUrl);

    SelfSignupCompletedEvent event =
        new SelfSignupCompletedEvent(
            context.getTenantId(),
            email,
            context.getAdminFirstName() != null ? context.getAdminFirstName() : "",
            context.getAdminLastName() != null ? context.getAdminLastName() : "",
            context.getOrganizationName() != null ? context.getOrganizationName() : "",
            context.getTaxId(),
            context.getOrganizationType() != null ? context.getOrganizationType().name() : null,
            setupUrl,
            context.isSalesLed(),
            context.getSubscriptionOsCodes(),
            context.getSignupIntent(),
            context.getTenantId(),
            LocalizationContext.getLocale());

    eventPublisher.publish(event);
    log.debug(
        "PublishSelfSignupCompletedStep: event published for {} (salesLed={})",
        email,
        context.isSalesLed());
  }
}
