package com.fabricmanagement.common.platform.communication.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

/**
 * Email Outbox - Transactional Outbox pattern for reliable email delivery.
 *
 * <p>Stores email requests in database before sending to ensure:
 *
 * <ul>
 *   <li>✅ Email persistence (survives application crashes)
 *   <li>✅ Transaction safety (email saved in same transaction as business logic)
 *   <li>✅ Retry capability (failed emails can be retried)
 *   <li>✅ Dead letter queue (permanently failed emails are tracked)
 * </ul>
 *
 * <p><b>Lifecycle:</b>
 *
 * <ol>
 *   <li>PENDING - Email queued, not yet sent
 *   <li>SENDING - Email being sent (background job)
 *   <li>SENT - Email sent successfully
 *   <li>FAILED - Email failed after max retries (dead letter queue)
 * </ol>
 */
@Entity
@Table(
    name = "communication_email_outbox",
    schema = "common_communication",
    indexes = {
      @Index(name = "idx_email_outbox_status", columnList = "status"),
      @Index(name = "idx_email_outbox_created_at", columnList = "created_at"),
      @Index(name = "idx_email_outbox_tenant", columnList = "tenant_id")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailOutbox extends BaseEntity {

  @Column(name = "recipient", nullable = false, length = 255)
  private String recipient;

  @Column(name = "subject", nullable = false, length = 500)
  private String subject;

  @Column(name = "html_body", nullable = false, columnDefinition = "TEXT")
  private String htmlBody;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  @Builder.Default
  private EmailOutboxStatus status = EmailOutboxStatus.PENDING;

  @Column(name = "retry_count", nullable = false)
  @Builder.Default
  private Integer retryCount = 0;

  @Column(name = "max_retries", nullable = false)
  @Builder.Default
  private Integer maxRetries = 3;

  @Column(name = "last_error", columnDefinition = "TEXT")
  private String lastError;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "next_retry_at")
  private Instant nextRetryAt;

  /** Create new email outbox entry. */
  public static EmailOutbox create(String recipient, String subject, String htmlBody) {
    return EmailOutbox.builder()
        .recipient(recipient)
        .subject(subject)
        .htmlBody(htmlBody)
        .status(EmailOutboxStatus.PENDING)
        .retryCount(0)
        .maxRetries(3)
        .build();
  }

  /** Mark email as sending (background job picked it up). */
  public void markAsSending() {
    this.status = EmailOutboxStatus.SENDING;
  }

  /** Mark email as sent successfully. */
  public void markAsSent() {
    this.status = EmailOutboxStatus.SENT;
    this.sentAt = Instant.now();
    this.lastError = null;
  }

  /** Mark email as failed and increment retry count. */
  public void markAsFailed(String error) {
    this.retryCount++;
    this.lastError = error;

    if (this.retryCount >= this.maxRetries) {
      // Max retries reached - move to dead letter queue
      this.status = EmailOutboxStatus.FAILED;
    } else {
      // Schedule next retry (exponential backoff: 1s, 2s, 4s)
      long delaySeconds = (long) Math.pow(2, this.retryCount - 1);
      this.nextRetryAt = Instant.now().plusSeconds(delaySeconds);
      this.status = EmailOutboxStatus.PENDING; // Retry later
    }
  }

  /** Check if email is ready for retry. */
  public boolean isReadyForRetry() {
    return this.status == EmailOutboxStatus.PENDING
        && this.retryCount < this.maxRetries
        && (this.nextRetryAt == null || Instant.now().isAfter(this.nextRetryAt));
  }

  /** Check if email is permanently failed (dead letter queue). */
  public boolean isPermanentlyFailed() {
    return this.status == EmailOutboxStatus.FAILED;
  }

  @Override
  protected String getModuleCode() {
    return "EMAIL";
  }
}
