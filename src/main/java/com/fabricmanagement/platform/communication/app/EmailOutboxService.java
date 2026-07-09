package com.fabricmanagement.platform.communication.app;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import com.fabricmanagement.platform.communication.domain.EmailOutbox;
import com.fabricmanagement.platform.communication.domain.EmailOutboxStatus;
import com.fabricmanagement.platform.communication.domain.strategy.EmailStrategy;
import com.fabricmanagement.platform.communication.infra.repository.EmailOutboxRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email Outbox Service - Background job for processing email queue.
 *
 * <p>Implements Transactional Outbox pattern:
 *
 * <ul>
 *   <li>✅ Email persistence (survives crashes)
 *   <li>✅ Retry mechanism (exponential backoff)
 *   <li>✅ Dead letter queue (permanently failed emails)
 *   <li>✅ Background processing (non-blocking)
 * </ul>
 *
 * <p><b>Background Job:</b> Runs every 5 seconds to process pending emails.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailOutboxService {

  private final EmailOutboxRepository emailOutboxRepository;
  private final EmailStrategy emailStrategy;
  private final EmailRecipientPolicy emailRecipientPolicy;
  private final SystemTransactionExecutor systemTransactionExecutor;
  private final MeterRegistry meterRegistry;

  @org.springframework.beans.factory.annotation.Value(
      "${application.email.dead-letter-alert-threshold:5}")
  private int deadLetterAlertThreshold;

  @org.springframework.beans.factory.annotation.Value(
      "${application.email.outbox.worker-enabled:true}")
  private boolean emailOutboxWorkerEnabled;

  @org.springframework.beans.factory.annotation.Value(
      "${application.email.outbox.dead-letter-monitor-enabled:true}")
  private boolean deadLetterMonitorEnabled;

  // Track last alert time to prevent spam
  private volatile long lastDeadLetterAlertTime = 0;
  private static final long DEAD_LETTER_ALERT_COOLDOWN_MS = 3600000; // 1 hour

  // Metrics
  private Counter emailSentCounter;
  private Counter emailFailedCounter;
  private Counter emailPermanentlyFailedCounter;

  /** Register Prometheus metrics for email monitoring. */
  @PostConstruct
  public void registerMetrics() {
    // Gauge: Current failed email count (dead letter queue size)
    Gauge.builder("email.outbox.failed.count", this, EmailOutboxService::getFailedEmailCount)
        .description("Number of permanently failed emails in dead letter queue")
        .tag("status", "failed")
        .register(meterRegistry);

    // Gauge: Current pending email count
    Gauge.builder("email.outbox.pending.count", this, EmailOutboxService::getPendingEmailCount)
        .description("Number of pending emails waiting to be sent")
        .tag("status", "pending")
        .register(meterRegistry);

    // Counter: Total emails sent successfully
    emailSentCounter =
        Counter.builder("email.outbox.sent.total")
            .description("Total number of emails sent successfully")
            .tag("status", "sent")
            .register(meterRegistry);

    // Counter: Total emails failed (transient failures)
    emailFailedCounter =
        Counter.builder("email.outbox.failed.total")
            .description("Total number of email send failures (transient)")
            .tag("status", "failed_transient")
            .register(meterRegistry);

    // Counter: Total emails permanently failed (dead letter queue)
    emailPermanentlyFailedCounter =
        Counter.builder("email.outbox.permanently_failed.total")
            .description("Total number of emails permanently failed (max retries reached)")
            .tag("status", "failed_permanent")
            .register(meterRegistry);

    log.info("✅ Email outbox metrics registered with Prometheus");
  }

  /**
   * Background job: Process pending emails every 5 seconds.
   *
   * <p>Fetches pending emails and attempts to send them. Failed emails are retried with exponential
   * backoff.
   *
   * <p><b>Performance:</b> Early return if no emails (minimal DB load). Query is optimized with
   * indexes for fast execution.
   */
  /**
   * Identity of a queued email, read without a tenant context.
   *
   * <p>The worker cannot ask JPA which emails are due: it runs on a scheduler thread where {@code
   * app.current_tenant} is unset, and every table here enforces row-level security. The query
   * returned zero rows, the worker concluded the queue was empty, and no email was ever sent — no
   * error, just blindness. So the due list is read through {@link SystemTransactionExecutor}
   * ({@code fabric_system}, BYPASSRLS), and each row is then loaded and updated inside its own
   * tenant context, where RLS applies normally.
   */
  private record PendingEmail(UUID id, UUID tenantId) {}

  private static final String DUE_EMAILS_SQL =
      """
      SELECT id, tenant_id
      FROM common_communication.communication_email_outbox
      WHERE status = 'PENDING'
        AND retry_count < max_retries
        AND is_active = true
        AND deleted_at IS NULL
        AND (next_retry_at IS NULL OR next_retry_at <= now())
      ORDER BY created_at ASC
      LIMIT 100
      """;

  @Scheduled(fixedDelayString = "${application.email.outbox.poll-interval-ms:5000}")
  public void processEmailQueue() {
    if (!emailOutboxWorkerEnabled) {
      log.trace("Email outbox worker disabled; skipping queue processing.");
      return;
    }
    try {
      List<PendingEmail> due =
          systemTransactionExecutor.executeQuery(
              DUE_EMAILS_SQL,
              (rs, rowNum) ->
                  new PendingEmail(
                      rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class)));

      if (due.isEmpty()) {
        return; // Nothing ready (no log spam)
      }

      log.info("📧 Processing {} pending email(s)", due.size());

      for (PendingEmail pending : due) {
        try {
          TenantContext.executeInTenantContext(
              pending.tenantId(),
              () ->
                  emailOutboxRepository
                      .findById(pending.id())
                      .ifPresentOrElse(
                          this::processEmail,
                          () ->
                              log.warn(
                                  "⚠️ Email {} is invisible inside tenant {}; skipping",
                                  pending.id(),
                                  pending.tenantId())));
        } catch (ObjectOptimisticLockingFailureException optimisticLockException) {
          log.warn("⚠️ Skipping email due to concurrent update: emailId={}", pending.id());
        } catch (Exception e) {
          log.error("Error processing email {}", pending.id(), e);
        }
      }

    } catch (Exception e) {
      log.error("Error processing email queue", e);
    }
  }

  /**
   * Process single email (send with retry logic).
   *
   * <p>Each {@code save} runs in its own transaction and returns a fresh managed instance with an
   * incremented {@code version}. The returned instance must replace the local one: keeping the
   * stale reference makes the next save write an outdated version, which fails the optimistic-lock
   * check. The email would already have been sent by then, so the row would sit in SENDING forever
   * with no error recorded — sent, but never marked as sent.
   */
  private void processEmail(EmailOutbox pending) {
    EmailOutbox email = pending;
    try {
      email.markAsSending();
      email = emailOutboxRepository.save(email);

      log.info(
          "📧 Sending email: recipient={}, retryCount={}",
          PiiMaskingUtil.maskEmail(email.getRecipient()),
          email.getRetryCount());

      // Send email via EmailStrategy
      emailStrategy.sendEmail(email.getRecipient(), email.getSubject(), email.getHtmlBody());

      // Success - mark as sent
      email.markAsSent();
      email = emailOutboxRepository.save(email);

      // Record metric
      emailSentCounter.increment();

      log.info(
          "✅ Email sent successfully: recipient={}, retryCount={}",
          PiiMaskingUtil.maskEmail(email.getRecipient()),
          email.getRetryCount());

    } catch (ObjectOptimisticLockingFailureException optimisticLockException) {
      log.warn("⚠️ Concurrent update detected while processing email: emailId={}", email.getId());
    } catch (Exception e) {
      // Failure - mark as failed and schedule retry
      String errorMsg = e.getMessage();
      if (errorMsg != null && errorMsg.length() > 500) {
        errorMsg = errorMsg.substring(0, 500) + "...";
      }

      email.markAsFailed(errorMsg);
      emailOutboxRepository.save(email);

      if (email.isPermanentlyFailed()) {
        // Record metric
        emailPermanentlyFailedCounter.increment();

        log.error(
            "❌ Email permanently failed (max retries reached): recipient={}, error={}",
            PiiMaskingUtil.maskEmail(email.getRecipient()),
            errorMsg);

        // Alert admin if dead letter queue threshold exceeded
        checkAndAlertDeadLetterQueue();
      } else {
        // Record metric (transient failure)
        emailFailedCounter.increment();

        log.warn(
            "⚠️ Email send failed, will retry: recipient={}, retryCount={}/{}, nextRetryAt={}, error={}",
            PiiMaskingUtil.maskEmail(email.getRecipient()),
            email.getRetryCount(),
            email.getMaxRetries(),
            email.getNextRetryAt(),
            errorMsg);
      }
    }
  }

  /**
   * Queue email for sending (Transactional Outbox pattern).
   *
   * <p>Email is saved to database in the same transaction as business logic. Background job will
   * process it later.
   *
   * <p><b>Sandboxing happens here</b>, not in {@link #processEmail}. This method still runs inside
   * the caller's tenant context; the worker does not. A row that reaches the queue is already
   * addressed to somewhere it is allowed to go.
   *
   * @param recipient Email recipient the caller intended
   * @param subject Email subject
   * @param htmlBody Email HTML body
   * @return the queued entity, or null when the tenant's sandbox has nowhere to redirect to and the
   *     email was dropped
   */
  @Transactional
  public EmailOutbox queueEmail(String recipient, String subject, String htmlBody) {
    EmailRecipientPolicy.Resolution resolution = emailRecipientPolicy.resolve(recipient, subject);
    if (resolution.dropped()) {
      return null;
    }

    EmailOutbox email =
        EmailOutbox.create(
            resolution.recipient(), resolution.intendedRecipient(), resolution.subject(), htmlBody);
    return emailOutboxRepository.save(email);
  }

  /**
   * Get pending email count (for monitoring).
   *
   * <p>Counted through {@code fabric_system}: these are called from metric gauges and a scheduled
   * alert, none of which carry a tenant context. Under RLS a tenant-scoped count from those threads
   * is always zero — which is exactly what the dead-letter alarm reported while 80 emails sat
   * unsent.
   */
  public long getPendingEmailCount() {
    return countByStatus(EmailOutboxStatus.PENDING);
  }

  /** Get failed email count (for monitoring). */
  public long getFailedEmailCount() {
    return countByStatus(EmailOutboxStatus.FAILED);
  }

  private long countByStatus(EmailOutboxStatus status) {
    Long count =
        systemTransactionExecutor.executeQueryForObject(
            "SELECT count(*) FROM common_communication.communication_email_outbox"
                + " WHERE status = ? AND is_active = true AND deleted_at IS NULL",
            (rs, rowNum) -> rs.getLong(1),
            status.name());
    return count == null ? 0L : count;
  }

  /**
   * Check dead letter queue and alert admin if threshold exceeded.
   *
   * <p>Prevents alert spam with cooldown period (1 hour).
   */
  private void checkAndAlertDeadLetterQueue() {
    long now = System.currentTimeMillis();

    // Cooldown check: Don't alert more than once per hour
    if (now - lastDeadLetterAlertTime < DEAD_LETTER_ALERT_COOLDOWN_MS) {
      return;
    }

    long failedCount = getFailedEmailCount();

    if (failedCount >= deadLetterAlertThreshold) {
      lastDeadLetterAlertTime = now;

      log.error(
          "🚨 DEAD LETTER QUEUE ALERT: {} failed email(s) in queue (threshold: {})",
          failedCount,
          deadLetterAlertThreshold);
      log.error(
          "🚨 Action required: Check email configuration, SMTP settings, or network connectivity");
      log.error(
          "🚨 Failed emails are stored in common_communication.communication_email_outbox WHERE status='FAILED'");
      log.error(
          "🚨 Prometheus metric: email.outbox.failed.count = {} (alert rule: DeadLetterQueueThresholdExceeded)",
          failedCount);
    }
  }

  /**
   * Scheduled job: Check dead letter queue periodically (every hour).
   *
   * <p>Proactive monitoring - alerts even if no new failures occur.
   */
  @Scheduled(cron = "${application.email.outbox.dead-letter-cron:0 0 * * * ?}")
  @Transactional(readOnly = true)
  public void monitorDeadLetterQueue() {
    if (!deadLetterMonitorEnabled) {
      log.trace("Email outbox dead letter monitor disabled; skipping check.");
      return;
    }
    long failedCount = getFailedEmailCount();

    if (failedCount >= deadLetterAlertThreshold) {
      log.error(
          "🚨 DEAD LETTER QUEUE MONITORING: {} failed email(s) in queue (threshold: {})",
          failedCount,
          deadLetterAlertThreshold);
      log.error("🚨 Review failed emails and investigate root cause");
    } else if (failedCount > 0) {
      log.warn(
          "⚠️ Dead letter queue has {} failed email(s) (below threshold: {})",
          failedCount,
          deadLetterAlertThreshold);
    }
  }
}
