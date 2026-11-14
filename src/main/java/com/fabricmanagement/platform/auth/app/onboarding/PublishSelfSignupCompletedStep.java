package com.fabricmanagement.platform.auth.app.onboarding;

import com.fabricmanagement.common.infrastructure.config.FrontendUrlProvider;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.web.LocalizationContext;
import com.fabricmanagement.platform.auth.domain.event.SelfSignupCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(15) // After registry provisioning and registered-demo seed; email sends after commit.
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

    String email = context.getAdminContact();
    if (email == null) {
      log.warn(
          "PublishSelfSignupCompletedStep: missing recipient email, skipping signup event. "
              + "tenantId={}, userId={}",
          context.getTenantId(),
          context.getUserId());
      return;
    }

    // Only sales-led onboarding mails a setup link. A self-service company is built after its
    // e-mail link was used, so the event carries no link; it still tells the platform team that a
    // company joined.
    String setupUrl = null;
    if (context.isSalesLed()) {
      String token = context.getRegistrationToken();
      if (token == null) {
        log.warn(
            "PublishSelfSignupCompletedStep: missing token, skipping signup email event. "
                + "tenantId={}, userId={}",
            context.getTenantId(),
            context.getUserId());
        return;
      }
      setupUrl = frontendUrlProvider.buildUrl("/setup?token=" + token);
      context.setSetupUrl(setupUrl);
    }

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
        "Signup email event published: eventId={}, tenantId={}, userId={}, salesLed={}",
        event.getEventId(),
        context.getTenantId(),
        context.getUserId(),
        context.isSalesLed());
  }
}
