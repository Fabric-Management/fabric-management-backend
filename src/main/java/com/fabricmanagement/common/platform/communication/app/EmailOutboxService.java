package com.fabricmanagement.common.platform.communication.app;

import com.fabricmanagement.common.platform.communication.domain.EmailOutbox;
import com.fabricmanagement.common.platform.communication.domain.EmailOutboxStatus;
import com.fabricmanagement.common.platform.communication.domain.strategy.EmailStrategy;
import com.fabricmanagement.common.platform.communication.infra.repository.EmailOutboxRepository;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * Background job: Process pending emails every 5 seconds.
     * 
     * <p>Fetches pending emails and attempts to send them.
     * Failed emails are retried with exponential backoff.</p>
     * 
     * <p><b>Performance:</b> Early return if no emails (minimal DB load).
     * Query is optimized with indexes for fast execution.</p>
     */
    @Scheduled(fixedDelay = 5000) // Every 5 seconds
    @Transactional
    public void processEmailQueue() {
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
                return; // No emails ready yet (waiting for retry time)
            }

            log.debug("Processing {} pending email(s)", pendingEmails.size());

            for (EmailOutbox email : pendingEmails) {
                processEmail(email);
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

            log.debug("Sending email: recipient={}, retryCount={}", 
                PiiMaskingUtil.maskEmail(email.getRecipient()), email.getRetryCount());

            // Send email via EmailStrategy
            emailStrategy.sendEmail(email.getRecipient(), email.getSubject(), email.getHtmlBody());

            // Success - mark as sent
            email.markAsSent();
            emailOutboxRepository.save(email);

            log.info("✅ Email sent successfully: recipient={}, retryCount={}", 
                PiiMaskingUtil.maskEmail(email.getRecipient()), email.getRetryCount());

        } catch (Exception e) {
            // Failure - mark as failed and schedule retry
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.length() > 500) {
                errorMsg = errorMsg.substring(0, 500) + "...";
            }
            
            email.markAsFailed(errorMsg);
            emailOutboxRepository.save(email);

            if (email.isPermanentlyFailed()) {
                log.error("❌ Email permanently failed (max retries reached): recipient={}, error={}", 
                    PiiMaskingUtil.maskEmail(email.getRecipient()), errorMsg);
                // TODO: Alert admin about dead letter queue
            } else {
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
}

