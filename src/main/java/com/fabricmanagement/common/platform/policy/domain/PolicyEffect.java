package com.fabricmanagement.common.platform.policy.domain;

/**
 * Policy effect - Result of a policy rule.
 *
 * <p>Amazon IAM-style policy effects:</p>
 * <ul>
 *   <li><b>ALLOW:</b> Explicitly grants access</li>
 *   <li><b>DENY:</b> Explicitly denies access (overrides ALLOW)</li>
 * </ul>
 *
 * <h2>Decision Priority:</h2>
 * <p>DENY always wins! Even if one ALLOW policy exists, a single DENY will block access.</p>
 */
public enum PolicyEffect {

    /**
     * Grant access if conditions match
     */
    ALLOW,

    /**
     * Deny access if conditions match (overrides ALLOW!)
     */
    DENY
}

