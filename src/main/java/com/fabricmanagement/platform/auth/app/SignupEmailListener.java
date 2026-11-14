package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.common.infrastructure.logging.LogSafeFailure;
import com.fabricmanagement.common.infrastructure.logging.SensitiveLogData;
import com.fabricmanagement.common.infrastructure.web.LocalizationService;
import com.fabricmanagement.platform.auth.domain.event.ExistingAccountSignupAttemptedEvent;
import com.fabricmanagement.platform.auth.domain.event.SelfSignupCompletedEvent;
import com.fabricmanagement.platform.auth.domain.event.SignupVerificationRequestedEvent;
import com.fabricmanagement.platform.communication.app.EmailOutboxService;
import com.fabricmanagement.platform.communication.app.EmailTemplateRenderer;
import com.fabricmanagement.platform.communication.app.NotificationService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Sends signup emails only after the transaction that produced them commits. */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignupEmailListener {

  /** At most this many "you already have an account" mails per address and day. */
  static final int MAX_EXISTING_ACCOUNT_NOTICES_PER_WINDOW = 3;

  static final Duration EXISTING_ACCOUNT_NOTICE_WINDOW = Duration.ofHours(24);

  private final NotificationService notificationService;
  private final EmailTemplateRenderer emailTemplateRenderer;
  private final LocalizationService localizationService;
  private final EmailOutboxService emailOutboxService;

  @Async
  @SensitiveLogData
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onSelfSignupCompleted(SelfSignupCompletedEvent event) {
    try {
      // A self-service company is built after its e-mail link was used, so the person already has
      // what a setup mail would give them. Only sales-led onboarding still mails a link.
      if (!event.isSalesLed()) {
        log.debug("Self-service signup completed: no mail to send. eventId={}", event.getEventId());
        return;
      }
      Locale locale = Locale.forLanguageTag(event.getLocaleLanguageTag());
      sendSalesLedWelcome(event, locale);
    } catch (RuntimeException failure) {
      log.error(
          "Signup email submission failed: eventId={}, tenantId={}",
          event.getEventId(),
          event.getTenantId(),
          LogSafeFailure.snapshot(failure));
      throw failure;
    }

    log.info(
        "Signup email submitted: eventId={}, tenantId={}, salesLed={}",
        event.getEventId(),
        event.getTenantId(),
        event.isSalesLed());
  }

  /**
   * Mails the link that proves the address of a pending signup (SIGNUP-DEFER-1). The event carries
   * the platform tenant id, because no tenant exists yet.
   */
  @Async
  @SensitiveLogData
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onSignupVerificationRequested(SignupVerificationRequestedEvent event) {
    try {
      Locale locale = Locale.forLanguageTag(event.getLocaleLanguageTag());
      String subject = localizationService.getMessage("email.registration.subject", null, locale);
      String message =
          emailTemplateRenderer.renderSignupConfirmation(
              event.getFirstName(),
              event.getOrganizationName(),
              event.getRecipientEmail(),
              event.getSetupUrl());
      notificationService.sendNotificationSync(
          event.getTenantId(), event.getRecipientEmail(), subject, message);
    } catch (RuntimeException failure) {
      log.error(
          "Signup confirmation email submission failed: eventId={}",
          event.getEventId(),
          LogSafeFailure.snapshot(failure));
      throw failure;
    }
    log.info("Signup confirmation email submitted: eventId={}", event.getEventId());
  }

  /** Tells an existing account holder to sign in and add the company instead of signing up. */
  @Async
  @SensitiveLogData
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onExistingAccountSignupAttempted(ExistingAccountSignupAttemptedEvent event) {
    // Anyone can type somebody else's address into the signup form. This notice is not tied to a
    // pending signup (which has its own link limit), so cap it per recipient instead.
    long recent =
        emailOutboxService.countQueuedTo(
            event.getTenantId(),
            event.getRecipientEmail(),
            Instant.now().minus(EXISTING_ACCOUNT_NOTICE_WINDOW));
    if (recent >= MAX_EXISTING_ACCOUNT_NOTICES_PER_WINDOW) {
      log.warn(
          "Existing-account signup notice suppressed (limit reached): eventId={}",
          event.getEventId());
      return;
    }
    try {
      Locale locale = Locale.forLanguageTag(event.getLocaleLanguageTag());
      String subject =
          localizationService.getMessage("email.signup.existing.subject", null, locale);
      String message =
          emailTemplateRenderer.renderExistingAccountNotice(
              event.getFirstName(), event.getRecipientEmail(), event.getSignInUrl());
      notificationService.sendNotificationSync(
          event.getTenantId(), event.getRecipientEmail(), subject, message);
    } catch (RuntimeException failure) {
      log.error(
          "Existing-account signup email submission failed: eventId={}",
          event.getEventId(),
          LogSafeFailure.snapshot(failure));
      throw failure;
    }
    log.info("Existing-account signup email submitted: eventId={}", event.getEventId());
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
