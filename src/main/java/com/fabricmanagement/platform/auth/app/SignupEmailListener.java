package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.common.infrastructure.web.LocalizationService;
import com.fabricmanagement.platform.auth.domain.event.SelfSignupCompletedEvent;
import com.fabricmanagement.platform.communication.app.EmailTemplateRenderer;
import com.fabricmanagement.platform.communication.app.NotificationService;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends signup setup/welcome email only after onboarding commits. */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignupEmailListener {

  private final NotificationService notificationService;
  private final EmailTemplateRenderer emailTemplateRenderer;
  private final LocalizationService localizationService;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onSelfSignupCompleted(SelfSignupCompletedEvent event) {
    Locale locale = Locale.forLanguageTag(event.getLocaleLanguageTag());

    if (event.isSalesLed()) {
      sendSalesLedWelcome(event, locale);
    } else {
      sendSelfServiceSetup(event, locale);
    }

    log.info(
        "Signup email sent: recipient={}, salesLed={}",
        maskEmail(event.getRecipientEmail()),
        event.isSalesLed());
  }

  private void sendSalesLedWelcome(SelfSignupCompletedEvent event, Locale locale) {
    String subject = localizationService.getMessage("email.welcome.subject", null, locale);
    String message =
        emailTemplateRenderer.renderWelcome(
            event.getFirstName(),
            event.getOrganizationName(),
            buildOsList(event.getSubscriptionOsCodes()),
            event.getSetupUrl());
    notificationService.sendNotificationSync(
        event.getTenantId(), event.getRecipientEmail(), subject, message);
  }

  private void sendSelfServiceSetup(SelfSignupCompletedEvent event, Locale locale) {
    String subject = localizationService.getMessage("email.registration.subject", null, locale);
    String message =
        emailTemplateRenderer.renderSetupPassword(
            event.getFirstName(),
            event.getOrganizationName(),
            event.getRecipientEmail(),
            event.getSetupUrl());
    notificationService.sendNotificationSync(
        event.getTenantId(), event.getRecipientEmail(), subject, message);
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

  private String maskEmail(String email) {
    if (email == null) {
      return "";
    }
    return email.replaceAll("(.).*@", "$1***@");
  }
}
