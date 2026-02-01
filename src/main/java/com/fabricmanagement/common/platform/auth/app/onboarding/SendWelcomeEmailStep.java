package com.fabricmanagement.common.platform.auth.app.onboarding;

import com.fabricmanagement.common.infrastructure.config.FrontendUrlProvider;
import com.fabricmanagement.common.platform.communication.app.EmailTemplateRenderer;
import com.fabricmanagement.common.platform.communication.app.NotificationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(7)
@Component
@RequiredArgsConstructor
@Slf4j
public class SendWelcomeEmailStep implements OnboardingStep {

  private final FrontendUrlProvider frontendUrlProvider;
  private final NotificationService notificationService;
  private final EmailTemplateRenderer emailTemplateRenderer;

  @Override
  public void execute(OnboardingContext context) {
    String token = context.getRegistrationToken();
    String email = context.getAdminContact();
    if (token == null || email == null) {
      return;
    }
    String setupUrl = frontendUrlProvider.buildUrl("/setup?token=" + token);
    context.setSetupUrl(setupUrl);

    List<String> osCodes = context.getSubscriptionOsCodes();
    String osList =
        osCodes != null && !osCodes.isEmpty()
            ? osCodes.stream()
                .map(os -> "<li style='margin: 10px 0; color: #374151;'>" + os + "</li>")
                .reduce((a, b) -> a + b)
                .orElse("<li style='margin: 10px 0; color: #374151;'>None</li>")
            : "<li style='margin: 10px 0; color: #374151;'>None</li>";

    String firstName = context.getAdminFirstName() != null ? context.getAdminFirstName() : "";
    String companyName = context.getCompanyName() != null ? context.getCompanyName() : "";
    String subject = "Welcome to FabricOS";
    String message = emailTemplateRenderer.renderWelcome(firstName, companyName, osList, setupUrl);
    notificationService.sendNotificationSync(email, subject, message);
    log.debug("SendWelcomeEmailStep: email sent to {}", email);
  }
}
