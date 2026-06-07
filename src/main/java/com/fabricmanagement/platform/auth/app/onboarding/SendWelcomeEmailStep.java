package com.fabricmanagement.platform.auth.app.onboarding;

import com.fabricmanagement.common.infrastructure.config.FrontendUrlProvider;
import com.fabricmanagement.common.infrastructure.web.LocalizationService;
import com.fabricmanagement.platform.communication.app.EmailTemplateRenderer;
import com.fabricmanagement.platform.communication.app.NotificationService;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(9) // After CreateRegistrationTokenStep (8) - FINAL STEP
@Component
@RequiredArgsConstructor
@Slf4j
public class SendWelcomeEmailStep implements OnboardingStep {

  private final FrontendUrlProvider frontendUrlProvider;
  private final NotificationService notificationService;
  private final EmailTemplateRenderer emailTemplateRenderer;
  private final LocalizationService localizationService;

  @Override
  public void execute(OnboardingContext context) {
    String token = context.getRegistrationToken();
    String email = context.getAdminContact();
    if (token == null || email == null) {
      return;
    }
    String setupUrl = frontendUrlProvider.buildUrl("/setup?token=" + token);
    context.setSetupUrl(setupUrl);

    String firstName = context.getAdminFirstName() != null ? context.getAdminFirstName() : "";
    String organizationName =
        context.getOrganizationName() != null ? context.getOrganizationName() : "";

    // Onboarding locale: use request Accept-Language (user hasn't set preferences yet)
    // Fallback: tenant's preferred locale may be set via OnboardingContext in the future
    Locale locale =
        Locale.forLanguageTag(
            com.fabricmanagement.common.infrastructure.web.LocalizationContext.getLocale());

    if (context.isSalesLed()) {
      // Sales-led: full welcome email with OS list
      String subject = localizationService.getMessage("email.welcome.subject", null, locale);
      String osList = buildOsList(context.getSubscriptionOsCodes());
      String message =
          emailTemplateRenderer.renderWelcome(firstName, organizationName, osList, setupUrl);
      notificationService.sendNotificationSync(email, subject, message);
    } else {
      // Self-service: setup-password email (email link click = verification)
      String subject = localizationService.getMessage("email.registration.subject", null, locale);
      String message =
          emailTemplateRenderer.renderSetupPassword(firstName, organizationName, email, setupUrl);
      notificationService.sendNotificationSync(email, subject, message);
    }
    log.debug("SendWelcomeEmailStep: email sent to {} (salesLed={})", email, context.isSalesLed());
  }

  private String buildOsList(List<String> osCodes) {
    if (osCodes == null || osCodes.isEmpty()) {
      return "<li style='margin: 10px 0; color: #374151;'>None</li>";
    }
    return osCodes.stream()
        .map(os -> "<li style='margin: 10px 0; color: #374151;'>" + os + "</li>")
        .reduce((a, b) -> a + b)
        .orElse("<li style='margin: 10px 0; color: #374151;'>None</li>");
  }
}
