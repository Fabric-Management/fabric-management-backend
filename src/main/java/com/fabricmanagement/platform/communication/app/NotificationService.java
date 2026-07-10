package com.fabricmanagement.platform.communication.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import com.fabricmanagement.platform.communication.domain.strategy.EmailStrategy;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Notification Service - Send notifications via email (async for better UX).
 *
 * <p>Email sending is asynchronous to prevent blocking user requests. Users get immediate response
 * while email is sent in background.
 *
 * <h2>Usage:</h2>
 *
 * <pre>{@code
 * // Async (default) - doesn't block user response
 * notificationService.sendNotification(
 *     "user@example.com",
 *     "Welcome to FabricOS",
 *     "Your account has been created..."
 * );
 *
 * // Sync (for critical emails that must be sent before response)
 * notificationService.sendNotificationSync(
 *     "user@example.com",
 *     "Security Alert",
 *     "Your password was changed..."
 * );
 * }</pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

  private final EmailStrategy emailStrategy;
  private final EmailOutboxService emailOutboxService;
  private final EmailRecipientPolicy emailRecipientPolicy;

  @Value("${application.email.use-outbox:true}")
  private boolean useOutbox;

  /**
   * Send notification via email (async - doesn't block user response).
   *
   * <p>Performance: Non-blocking async execution ensures fast user responses. Email sending happens
   * in background thread pool.
   *
   * <p><b>Reliability:</b> Uses Transactional Outbox pattern if enabled. Email is saved to database
   * first, then processed by background job. This ensures email persistence and retry capability.
   *
   * @param recipient Email address
   * @param subject Email subject
   * @param message Email body
   */
  @Async
  public void sendNotification(String recipient, String subject, String message) {
    sendNotification(TenantContext.requireTenantId(), recipient, subject, message);
  }

  @Async
  public void sendNotification(UUID tenantId, String recipient, String subject, String message) {
    log.info("Sending notification (async) to: {}", PiiMaskingUtil.maskEmail(recipient));

    try {
      if (useOutbox) {
        // Transactional Outbox pattern: Save to DB, background job will send.
        // queueEmail applies the sandbox policy itself.
        emailOutboxService.queueEmail(tenantId, recipient, subject, message);
        log.info("✅ Email queued for sending: recipient={}", PiiMaskingUtil.maskEmail(recipient));
      } else {
        sendDirectly(tenantId, recipient, subject, message);
      }
    } catch (Exception e) {
      log.error("❌ Failed to send notification to: {}", PiiMaskingUtil.maskEmail(recipient), e);
    }
  }

  /**
   * Send notification synchronously (for critical emails).
   *
   * <p><b>Note:</b> Even in sync mode, if outbox is enabled, email is queued. For truly synchronous
   * sending, set {@code application.email.use-outbox=false}.
   *
   * @param recipient Email address
   * @param subject Email subject
   * @param message Email body
   */
  public void sendNotificationSync(String recipient, String subject, String message) {
    sendNotificationSync(TenantContext.requireTenantId(), recipient, subject, message);
  }

  public void sendNotificationSync(
      UUID tenantId, String recipient, String subject, String message) {
    log.info("Sending notification (sync) to: {}", PiiMaskingUtil.maskEmail(recipient));

    if (useOutbox) {
      // Queue email (still persisted, but called synchronously). Sandbox applied in queueEmail.
      emailOutboxService.queueEmail(tenantId, recipient, subject, message);
      log.info("✅ Email queued (sync) to: {}", PiiMaskingUtil.maskEmail(recipient));
    } else {
      sendDirectly(tenantId, recipient, subject, message);
    }
  }

  /**
   * Legacy path taken when {@code application.email.use-outbox=false}. It bypasses the outbox, so
   * it must apply the sandbox policy itself — this branch was the reason a rewrite confined to
   * {@code queueEmail} would not have been a choke point.
   *
   * <p>Replaces the previous playground safeguard, which dropped the email outright and, worse,
   * recognised playgrounds by {@code type == PLAYGROUND} while the supported register-first
   * playground carries {@code type=REGULAR}. It therefore protected nothing. Playground email is
   * now redirected to the prospect rather than discarded: receiving it is part of the demo.
   */
  private void sendDirectly(UUID tenantId, String recipient, String subject, String message) {
    EmailRecipientPolicy.Resolution resolution =
        emailRecipientPolicy.resolveFor(tenantId, recipient, subject);
    if (resolution.dropped()) {
      return;
    }
    emailStrategy.sendEmail(resolution.recipient(), resolution.subject(), message);
    log.info("✅ Notification sent to: {}", PiiMaskingUtil.maskEmail(resolution.recipient()));
  }
}
