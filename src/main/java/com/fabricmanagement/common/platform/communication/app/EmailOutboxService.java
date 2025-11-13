package com.fabricmanagement.common.platform.communication.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.communication.domain.EmailOutbox;
import com.fabricmanagement.common.platform.communication.domain.EmailOutboxStatus;
import com.fabricmanagement.common.platform.communication.domain.strategy.EmailStrategy;
import com.fabricmanagement.common.platform.communication.infra.repository.EmailOutboxRepository;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Email Outbox Service - Background job for processing email queue.
 * 
 * <p>Implements Transactional Outbox pattern:
 * <ul>
 *   <li>✅ Email persistence (survives crashes)</li>
 *   <li>✅ Retry mechanism (exponential backoff)</li>
 *   <li>✅ Dead letter queue (permanently failed emails)</li>
 *   <li>✅ Background processing (non-blocking)</li>
 * </ul>
 * 
 * <p><b>Background Job:</b> Runs every 5 seconds to process pending emails.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailOutboxService {

    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailStrategy emailStrategy;
    private final MeterRegistry meterRegistry;

    @org.springframework.beans.factory.annotation.Value("${application.email.dead-letter-alert-threshold:5}")
    private int deadLetterAlertThreshold;

    @org.springframework.beans.factory.annotation.Value("${application.email.outbox.worker-enabled:true}")
    private boolean emailOutboxWorkerEnabled;

    @org.springframework.beans.factory.annotation.Value("${application.email.outbox.dead-letter-monitor-enabled:true}")
    private boolean deadLetterMonitorEnabled;

    // Track last alert time to prevent spam
    private volatile long lastDeadLetterAlertTime = 0;
    private static final long DEAD_LETTER_ALERT_COOLDOWN_MS = 3600000; // 1 hour

    // Metrics
    private Counter emailSentCounter;
    private Counter emailFailedCounter;
    private Counter emailPermanentlyFailedCounter;

    /**
     * Register Prometheus metrics for email monitoring.
     */
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
        emailSentCounter = Counter.builder("email.outbox.sent.total")
            .description("Total number of emails sent successfully")
            .tag("status", "sent")
            .register(meterRegistry);

        // Counter: Total emails failed (transient failures)
        emailFailedCounter = Counter.builder("email.outbox.failed.total")
            .description("Total number of email send failures (transient)")
            .tag("status", "failed_transient")
            .register(meterRegistry);

        // Counter: Total emails permanently failed (dead letter queue)
        emailPermanentlyFailedCounter = Counter.builder("email.outbox.permanently_failed.total")
            .description("Total number of emails permanently failed (max retries reached)")
            .tag("status", "failed_permanent")
            .register(meterRegistry);

        log.info("✅ Email outbox metrics registered with Prometheus");
    }

    /**
     * Background job: Process pending emails every 5 seconds.
     * 
     * <p>Fetches pending emails and attempts to send them.
     * Failed emails are retried with exponential backoff.</p>
     * 
     * <p><b>Performance:</b> Early return if no emails (minimal DB load).
     * Query is optimized with indexes for fast execution.</p>
     */
    @Scheduled(fixedDelayString = "${application.email.outbox.poll-interval-ms:5000}")
    @Transactional
    public void processEmailQueue() {
        if (!emailOutboxWorkerEnabled) {
            log.trace("Email outbox worker disabled; skipping queue processing.");
            return;
        }
        try {
            // Fast check: Count pending emails first (uses index)
            long pendingCount = emailOutboxRepository.countByStatusAndIsActiveTrue(EmailOutboxStatus.PENDING);
            if (pendingCount == 0) {
                // No emails to process - early return (no log spam)
                return;
            }

            // Fetch pending emails ready for sending
            List<EmailOutbox> pendingEmails = emailOutboxRepository.findPendingEmailsReadyForSending(
                EmailOutboxStatus.PENDING,
                Instant.now()
            );

            if (pendingEmails.isEmpty()) {
                log.debug("📧 Found {} pending email(s) but none ready for sending yet (waiting for retry time)", pendingCount);
                return; // No emails ready yet (waiting for retry time)
            }

            log.info("📧 Processing {} pending email(s) (total pending: {})", pendingEmails.size(), pendingCount);

            for (EmailOutbox email : pendingEmails) {
                var tenantId = email.getTenantId();
                try {
                    TenantContext.executeInTenantContext(tenantId, () -> processEmail(email));
                } catch (ObjectOptimisticLockingFailureException optimisticLockException) {
                    log.warn("⚠️ Skipping email due to concurrent update: emailId={}", email.getId());
                }
            }

        } catch (Exception e) {
            log.error("Error processing email queue", e);
        }
    }

    /**
     * Process single email (send with retry logic).
     */
    private void processEmail(EmailOutbox email) {
        try {
            email.markAsSending();
            emailOutboxRepository.save(email);

            log.info("📧 Sending email: recipient={}, retryCount={}", 
                PiiMaskingUtil.maskEmail(email.getRecipient()), email.getRetryCount());

            // Send email via EmailStrategy
            emailStrategy.sendEmail(email.getRecipient(), email.getSubject(), email.getHtmlBody());

            // Success - mark as sent
            email.markAsSent();
            emailOutboxRepository.save(email);
            
            // Record metric
            emailSentCounter.increment();

            log.info("✅ Email sent successfully: recipient={}, retryCount={}", 
                PiiMaskingUtil.maskEmail(email.getRecipient()), email.getRetryCount());

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
                
                log.error("❌ Email permanently failed (max retries reached): recipient={}, error={}", 
                    PiiMaskingUtil.maskEmail(email.getRecipient()), errorMsg);
                
                // Alert admin if dead letter queue threshold exceeded
                checkAndAlertDeadLetterQueue();
            } else {
                // Record metric (transient failure)
                emailFailedCounter.increment();
                
                log.warn("⚠️ Email send failed, will retry: recipient={}, retryCount={}/{}, nextRetryAt={}, error={}", 
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
     * <p>Email is saved to database in the same transaction as business logic.
     * Background job will process it later.</p>
     * 
     * @param recipient Email recipient
     * @param subject Email subject
     * @param htmlBody Email HTML body
     * @return EmailOutbox entity
     */
    @Transactional
    public EmailOutbox queueEmail(String recipient, String subject, String htmlBody) {
        EmailOutbox email = EmailOutbox.create(recipient, subject, htmlBody);
        return emailOutboxRepository.save(email);
    }

    /**
     * Get pending email count (for monitoring).
     */
    @Transactional(readOnly = true)
    public long getPendingEmailCount() {
        return emailOutboxRepository.countByStatusAndIsActiveTrue(EmailOutboxStatus.PENDING);
    }

    /**
     * Get failed email count (for monitoring).
     */
    @Transactional(readOnly = true)
    public long getFailedEmailCount() {
        return emailOutboxRepository.countByStatusAndIsActiveTrue(EmailOutboxStatus.FAILED);
    }

    /**
     * Check dead letter queue and alert admin if threshold exceeded.
     * 
     * <p>Prevents alert spam with cooldown period (1 hour).</p>
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
            
            log.error("🚨 DEAD LETTER QUEUE ALERT: {} failed email(s) in queue (threshold: {})", 
                failedCount, deadLetterAlertThreshold);
            log.error("🚨 Action required: Check email configuration, SMTP settings, or network connectivity");
            log.error("🚨 Failed emails are stored in common_communication.communication_email_outbox WHERE status='FAILED'");
            log.error("🚨 Prometheus metric: email.outbox.failed.count = {} (alert rule: DeadLetterQueueThresholdExceeded)", failedCount);
        }
    }

    /**
     * Scheduled job: Check dead letter queue periodically (every hour).
     * 
     * <p>Proactive monitoring - alerts even if no new failures occur.</p>
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
            log.error("🚨 DEAD LETTER QUEUE MONITORING: {} failed email(s) in queue (threshold: {})", 
                failedCount, deadLetterAlertThreshold);
            log.error("🚨 Review failed emails and investigate root cause");
        } else if (failedCount > 0) {
            log.warn("⚠️ Dead letter queue has {} failed email(s) (below threshold: {})", 
                failedCount, deadLetterAlertThreshold);
        }
    }
}

