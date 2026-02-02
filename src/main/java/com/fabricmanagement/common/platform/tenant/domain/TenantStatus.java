package com.fabricmanagement.common.platform.tenant.domain;

/**
 * Tenant lifecycle status.
 *
 * <p>Represents the subscription/billing state of a tenant on the platform.
 *
 * <h2>Status Transitions:</h2>
 *
 * <pre>
 * TRIAL → ACTIVE (payment received)
 * TRIAL → SUSPENDED (trial expired, no payment)
 * ACTIVE → SUSPENDED (payment failed / policy violation)
 * SUSPENDED → ACTIVE (payment received / issue resolved)
 * SUSPENDED → CANCELLED (prolonged non-payment / user request)
 * </pre>
 */
public enum TenantStatus {

  /**
   * Trial period - limited time, full features.
   *
   * <p>Default status for new tenants. Transitions to ACTIVE after payment or SUSPENDED after trial
   * expiry.
   */
  TRIAL,

  /**
   * Active subscription - paid and operational.
   *
   * <p>Tenant has full access to subscribed features.
   */
  ACTIVE,

  /**
   * Suspended - temporarily disabled.
   *
   * <p>Caused by payment failure, policy violation, or trial expiry. Data preserved, access
   * restricted. Can transition back to ACTIVE.
   */
  SUSPENDED,

  /**
   * Cancelled - permanently disabled.
   *
   * <p>Tenant requested cancellation or prolonged suspension. Data retention policy applies (e.g.,
   * 90 days). Terminal state.
   */
  CANCELLED;

  /**
   * Check if tenant can access the platform.
   *
   * @return true if tenant has platform access
   */
  public boolean hasAccess() {
    return this == TRIAL || this == ACTIVE;
  }

  /**
   * Check if this is a terminal state.
   *
   * @return true if tenant cannot be reactivated
   */
  public boolean isTerminal() {
    return this == CANCELLED;
  }
}
