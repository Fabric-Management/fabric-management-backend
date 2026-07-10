package com.fabricmanagement.platform.auth.app;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.web.LocalizationContext;
import com.fabricmanagement.common.infrastructure.web.LocalizationService;
import com.fabricmanagement.platform.auth.domain.event.SelfSignupCompletedEvent;
import com.fabricmanagement.platform.communication.app.EmailTemplateRenderer;
import com.fabricmanagement.platform.communication.app.NotificationService;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SignupEmailListenerTest {

  @Mock private NotificationService notificationService;
  @Mock private EmailTemplateRenderer emailTemplateRenderer;
  @Mock private LocalizationService localizationService;

  @AfterEach
  void tearDown() {
    LocalizationContext.clear();
  }

  @Test
  void sendsSelfServiceSetupPasswordEmailFromEventPayload() {
    SignupEmailListener listener =
        new SignupEmailListener(notificationService, emailTemplateRenderer, localizationService);
    SelfSignupCompletedEvent event =
        new SelfSignupCompletedEvent(
            UUID.randomUUID(),
            "owner@example.com",
            "Owner",
            "Admin",
            "Acme Textiles",
            "ACME-123",
            "SPINNER",
            "https://app.example.com/setup?token=abc",
            false,
            List.of("FabricOS"),
            "PLAYGROUND",
            UUID.randomUUID(),
            "en");
    LocalizationContext.setLocale("tr");
    when(localizationService.getMessage(
            "email.registration.subject", null, Locale.forLanguageTag("en")))
        .thenReturn("Set up your password");
    when(emailTemplateRenderer.renderSetupPassword(
            "Owner",
            "Acme Textiles",
            "owner@example.com",
            "https://app.example.com/setup?token=abc"))
        .thenReturn("setup body");

    listener.onSelfSignupCompleted(event);

    verify(localizationService)
        .getMessage("email.registration.subject", null, Locale.forLanguageTag("en"));
    verify(emailTemplateRenderer)
        .renderSetupPassword(
            "Owner",
            "Acme Textiles",
            "owner@example.com",
            "https://app.example.com/setup?token=abc");
    verify(emailTemplateRenderer, never())
        .renderWelcome(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString());
    verify(notificationService)
        .sendNotificationSync(
            event.getTenantId(), "owner@example.com", "Set up your password", "setup body");
  }

  @Test
  void sendsSalesLedWelcomeEmailFromEventPayload() {
    SignupEmailListener listener =
        new SignupEmailListener(notificationService, emailTemplateRenderer, localizationService);
    SelfSignupCompletedEvent event =
        new SelfSignupCompletedEvent(
            UUID.randomUUID(),
            "admin@example.com",
            "Admin",
            "Owner",
            "Enterprise Textiles",
            "ENT-123",
            "VERTICAL_MILL",
            "https://app.example.com/setup?token=sales",
            true,
            List.of("FabricOS", "WarehouseOS"),
            "SALES_LED",
            UUID.randomUUID(),
            "tr");
    when(localizationService.getMessage("email.welcome.subject", null, Locale.forLanguageTag("tr")))
        .thenReturn("Welcome");
    when(emailTemplateRenderer.renderWelcome(
            org.mockito.ArgumentMatchers.eq("Admin"),
            org.mockito.ArgumentMatchers.eq("Enterprise Textiles"),
            org.mockito.ArgumentMatchers.contains("FabricOS"),
            org.mockito.ArgumentMatchers.eq("https://app.example.com/setup?token=sales")))
        .thenReturn("welcome body");

    listener.onSelfSignupCompleted(event);

    verify(localizationService)
        .getMessage("email.welcome.subject", null, Locale.forLanguageTag("tr"));
    verify(emailTemplateRenderer)
        .renderWelcome(
            org.mockito.ArgumentMatchers.eq("Admin"),
            org.mockito.ArgumentMatchers.eq("Enterprise Textiles"),
            org.mockito.ArgumentMatchers.contains("FabricOS"),
            org.mockito.ArgumentMatchers.eq("https://app.example.com/setup?token=sales"));
    verify(emailTemplateRenderer, never())
        .renderSetupPassword(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString());
    verify(notificationService)
        .sendNotificationSync(event.getTenantId(), "admin@example.com", "Welcome", "welcome body");
  }
}
