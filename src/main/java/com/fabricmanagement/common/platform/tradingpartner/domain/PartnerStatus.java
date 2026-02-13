package com.fabricmanagement.common.platform.tradingpartner.domain;

/**
 * Trading partner relationship status within a tenant.
 *
 * <p>Tracks the lifecycle of a business relationship between a tenant and a trading partner.
 *
 * <h2>Lifecycle:</h2>
 *
 * <pre>
 * ACTIVE ←→ SUSPENDED
 *    ↓
 * BLOCKED
 *
 * INVITED → PENDING_APPROVAL → ACTIVE
 * </pre>
 */
public enum PartnerStatus {

  /** Active business relationship - transactions allowed */
  ACTIVE,

  /** Invited to platform but not yet registered */
  INVITED,

  /** Pending approval from partner or internal review */
  PENDING_APPROVAL,

  /** Temporarily suspended - no new transactions */
  SUSPENDED,

  /** Blocked - relationship terminated, no transactions allowed */
  BLOCKED;

  /**
   * Check if transactions are allowed with this status.
   *
   * @return true if ACTIVE
   */
  public boolean isTransactionAllowed() {
    return this == ACTIVE;
  }

  /**
   * Check if this status can transition to ACTIVE.
   *
   * @return true if INVITED, PENDING_APPROVAL, or SUSPENDED
   */
  public boolean canActivate() {
    return this == INVITED || this == PENDING_APPROVAL || this == SUSPENDED;
  }
}
