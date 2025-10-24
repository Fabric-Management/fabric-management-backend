package com.fabricmanagement.common.platform.company.domain;

/**
 * Subscription lifecycle states.
 *
 * <p>Represents the current status of an OS subscription.
 * Transitions follow strict lifecycle rules.</p>
 *
 * <h2>Lifecycle Flow:</h2>
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
     * <p>Transition: TRIAL → ACTIVE (payment) or EXPIRED (trial ends)</p>
     */
    TRIAL,

    /**
     * Active subscription - Paid and valid
     * <p>Transition: ACTIVE → EXPIRED (expiry) or SUSPENDED (payment issue)</p>
     */
    ACTIVE,

    /**
     * Expired - Subscription period ended
     * <p>Access blocked or read-only mode</p>
     */
    EXPIRED,

    /**
     * Cancelled - Manually cancelled by user
     * <p>No reactivation allowed (must create new subscription)</p>
     */
    CANCELLED,

    /**
     * Suspended - Payment issue or policy violation
     * <p>Transition: SUSPENDED → ACTIVE (issue resolved)</p>
     */
    SUSPENDED
}

