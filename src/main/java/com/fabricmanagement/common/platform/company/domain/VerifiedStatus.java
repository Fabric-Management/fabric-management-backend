package com.fabricmanagement.common.platform.company.domain;

/**
 * Platform-level verification status for trading partner registry.
 *
 * <p>Indicates whether a partner's identity has been verified at the platform level. Verification
 * can occur through:
 *
 * <ul>
 *   <li>Partner linking to an existing platform tenant
 *   <li>Manual verification by platform admin
 *   <li>Third-party verification service
 * </ul>
 *
 * <h2>Lifecycle:</h2>
 *
 * <pre>
 * UNVERIFIED → PENDING → VERIFIED
 * </pre>
 */
public enum VerifiedStatus {

  /** Not verified - default for new entries */
  UNVERIFIED,

  /** Verification in progress */
  PENDING,

  /** Verified by platform, linked tenant, or third-party */
  VERIFIED;

  /**
   * Check if this partner identity is confirmed.
   *
   * @return true if VERIFIED
   */
  public boolean isVerified() {
    return this == VERIFIED;
  }
}
