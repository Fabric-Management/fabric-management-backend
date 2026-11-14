package com.fabricmanagement.platform.auth.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.web.LocalizationContext;
import com.fabricmanagement.common.infrastructure.web.LocalizationService;
import com.fabricmanagement.platform.auth.domain.event.ExistingAccountSignupAttemptedEvent;
import com.fabricmanagement.platform.auth.domain.event.SelfSignupCompletedEvent;
import com.fabricmanagement.platform.auth.domain.event.SignupVerificationRequestedEvent;
import com.fabricmanagement.platform.communication.app.EmailOutboxService;
import com.fabricmanagement.platform.communication.app.EmailTemplateRenderer;
import com.fabricmanagement.platform.communication.app.NotificationService;
import java.time.Instant;
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
  @Mock private EmailOutboxService emailOutboxService;

  @AfterEach
  void tearDown() {
    LocalizationContext.clear();
  }

  @Test
  void sendsNoMailForACompletedSelfServiceSignup() {
    SignupEmailListener listener =
        new SignupEmailListener(
            notificationService, emailTemplateRenderer, localizationService, emailOutboxService);
    SelfSignupCompletedEvent event =
        new SelfSignupCompletedEvent(
            UUID.randomUUID(),
            "owner@example.com",
            "Owner",
            "Admin",
            "Acme Textiles",
            "ACME-123",
            "SPINNER",
            null,
            false,
            List.of("FabricOS"),
            "PLAYGROUND",
            UUID.randomUUID(),
            "en");

    // The company is built after its e-mail link was used: the person already has what a setup
    // mail would give them, so the completed event is only for the platform team's notice.
    listener.onSelfSignupCompleted(event);

    verifyNoInteractions(notificationService, emailTemplateRenderer, localizationService);
  }

  @Test
  void sendsSalesLedWelcomeEmailFromEventPayload() {
    SignupEmailListener listener =
        new SignupEmailListener(
            notificationService, emailTemplateRenderer, localizationService, emailOutboxService);
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

  @Test
  void mailsTheSignupConfirmationLinkFromThePlatformTenant() {
    SignupEmailListener listener =
        new SignupEmailListener(
            notificationService, emailTemplateRenderer, localizationService, emailOutboxService);
    UUID platformTenant = UUID.randomUUID();
    var event =
        new SignupVerificationRequestedEvent(
            platformTenant,
            "owner@example.com",
            "Owner",
            "Acme Textiles",
            "https://app.example.com/setup?token=abc",
            "en");
    when(localizationService.getMessage(
            "email.registration.subject", null, Locale.forLanguageTag("en")))
        .thenReturn("Complete Your FabricOS Registration");
    when(emailTemplateRenderer.renderSignupConfirmation(
            "Owner",
            "Acme Textiles",
            "owner@example.com",
            "https://app.example.com/setup?token=abc"))
        .thenReturn("<html>confirm</html>");

    listener.onSignupVerificationRequested(event);

    verify(notificationService)
        .sendNotificationSync(
            platformTenant,
            "owner@example.com",
            "Complete Your FabricOS Registration",
            "<html>confirm</html>");
  }

  @Test
  void tellsAnExistingAccountHolderToSignInUnlessTheDailyLimitIsReached() {
    SignupEmailListener listener =
        new SignupEmailListener(
            notificationService, emailTemplateRenderer, localizationService, emailOutboxService);
    UUID platformTenant = UUID.randomUUID();
    var event =
        new ExistingAccountSignupAttemptedEvent(
            platformTenant, "owner@example.com", "Owner", "https://app.example.com/login", "en");
    when(emailOutboxService.countQueuedTo(
            eq(platformTenant), eq("owner@example.com"), any(Instant.class)))
        .thenReturn((long) SignupEmailListener.MAX_EXISTING_ACCOUNT_NOTICES_PER_WINDOW - 1);
    when(localizationService.getMessage(
            "email.signup.existing.subject", null, Locale.forLanguageTag("en")))
        .thenReturn("You already have a FabricOS account");
    when(emailTemplateRenderer.renderExistingAccountNotice(
            "Owner", "owner@example.com", "https://app.example.com/login"))
        .thenReturn("<html>sign in</html>");

    listener.onExistingAccountSignupAttempted(event);

    verify(notificationService)
        .sendNotificationSync(
            platformTenant,
            "owner@example.com",
            "You already have a FabricOS account",
            "<html>sign in</html>");
  }

  @Test
  void sendsNoFurtherExistingAccountNoticeOnceTheDailyLimitIsReached() {
    SignupEmailListener listener =
        new SignupEmailListener(
            notificationService, emailTemplateRenderer, localizationService, emailOutboxService);
    UUID platformTenant = UUID.randomUUID();
    var event =
        new ExistingAccountSignupAttemptedEvent(
            platformTenant, "owner@example.com", "Owner", "https://app.example.com/login", "en");
    when(emailOutboxService.countQueuedTo(
            eq(platformTenant), eq("owner@example.com"), any(Instant.class)))
        .thenReturn((long) SignupEmailListener.MAX_EXISTING_ACCOUNT_NOTICES_PER_WINDOW);

    listener.onExistingAccountSignupAttempted(event);

    verify(notificationService, never()).sendNotificationSync(any(), any(), any(), any());
  }
}
