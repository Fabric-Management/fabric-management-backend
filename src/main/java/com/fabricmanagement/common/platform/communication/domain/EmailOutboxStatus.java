package com.fabricmanagement.common.platform.communication.domain;

/** Email Outbox Status - Lifecycle states for email delivery. */
public enum EmailOutboxStatus {
  /** Email queued, waiting to be sent. */
  PENDING,

  /** Email being sent (background job processing). */
  SENDING,

  /** Email sent successfully. */
  SENT,

  /** Email failed after max retries (dead letter queue). */
  FAILED
}
