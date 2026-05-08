package com.fabricmanagement.platform.communication.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import com.fabricmanagement.platform.communication.domain.strategy.EmailStrategy;
import com.fabricmanagement.platform.tenant.domain.TenantType;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
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
  private final TenantRepository tenantRepository;

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
    if (isPlaygroundTenant()) {
      log.info(
          "🛡️ [PLAYGROUND SAFEGUARD] Skipping async email notification to: {} (Subject: {})",
          PiiMaskingUtil.maskEmail(recipient),
          subject);
      return;
    }

    log.info("Sending notification (async) to: {}", PiiMaskingUtil.maskEmail(recipient));

    try {
      if (useOutbox) {
        // Transactional Outbox pattern: Save to DB, background job will send
        emailOutboxService.queueEmail(recipient, subject, message);
        log.info("✅ Email queued for sending: recipient={}", PiiMaskingUtil.maskEmail(recipient));
      } else {
        // Direct send (legacy mode - no persistence)
        emailStrategy.sendEmail(recipient, subject, message);
        log.info("✅ Notification sent successfully to: {}", PiiMaskingUtil.maskEmail(recipient));
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
    if (isPlaygroundTenant()) {
      log.info(
          "🛡️ [PLAYGROUND SAFEGUARD] Skipping sync email notification to: {} (Subject: {})",
          PiiMaskingUtil.maskEmail(recipient),
          subject);
      return;
    }

    log.info("Sending notification (sync) to: {}", PiiMaskingUtil.maskEmail(recipient));

    if (useOutbox) {
      // Queue email (still persisted, but called synchronously)
      emailOutboxService.queueEmail(recipient, subject, message);
      log.info("✅ Email queued (sync) to: {}", PiiMaskingUtil.maskEmail(recipient));
    } else {
      // Direct send (legacy mode)
      emailStrategy.sendEmail(recipient, subject, message);
      log.info("✅ Notification sent (sync) to: {}", PiiMaskingUtil.maskEmail(recipient));
    }
  }

  private boolean isPlaygroundTenant() {
    UUID tenantId = TenantContext.getCurrentTenantIdOrNull();
    if (tenantId == null) {
      return false;
    }
    return tenantRepository
        .findById(tenantId)
        .map(tenant -> tenant.getType() == TenantType.PLAYGROUND)
        .orElse(false);
  }
}
