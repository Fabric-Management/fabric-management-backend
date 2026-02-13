package com.fabricmanagement.common.platform.subscription.domain;

/**
 * Subscription lifecycle states.
 *
 * <p>Represents the current status of an OS subscription. Transitions follow strict lifecycle
 * rules.
 *
 * <h2>Lifecycle Flow:</h2>
 *
 * <pre>
 * TRIAL → ACTIVE → EXPIRED
 *   │       │         ↑
 *   │       ↓         │
 *   │   SUSPENDED ────┘
 *   │       ↓
 *   └───> CANCELLED
 * </pre>
 */
public enum SubscriptionStatus {

  /**
   * Trial period - Free access for limited time
   *
   * <p>Transition: TRIAL → ACTIVE (payment) or EXPIRED (trial ends)
   */
  TRIAL,

  /**
   * Active subscription - Paid and valid
   *
   * <p>Transition: ACTIVE → EXPIRED (expiry) or SUSPENDED (payment issue)
   */
  ACTIVE,

  /**
   * Expired - Subscription period ended
   *
   * <p>Access blocked or read-only mode
   */
  EXPIRED,

  /**
   * Cancelled - Manually cancelled by user
   *
   * <p>No reactivation allowed (must create new subscription)
   */
  CANCELLED,

  /**
   * Suspended - Payment issue or policy violation
   *
   * <p>Transition: SUSPENDED → ACTIVE (issue resolved)
   */
  SUSPENDED
}
