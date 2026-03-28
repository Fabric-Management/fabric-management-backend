package com.fabricmanagement.platform.communication.domain;

/** Status of an outbound notification/verification message */
public enum DeliveryStatus {
  /** Message is queued and waiting to be sent */
  PENDING,

  /** Message sent to provider but not yet delivered */
  SENT,

  /** Message was successfully delivered to recipient */
  DELIVERED,

  /** Message was successfully accepted by provider/delivered (legacy) */
  SUCCESS,

  /** Message failed to deliver (invalid number, hard bounce) */
  FAILED,

  /** Message sent but receipt not confirmed in time. Triggers fallback. */
  TIMEOUT
}
