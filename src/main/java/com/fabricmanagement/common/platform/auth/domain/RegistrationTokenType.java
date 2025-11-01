package com.fabricmanagement.common.platform.auth.domain;

/**
 * Registration token type - Different flows for different user acquisition channels.
 *
 * <p>Token types:</p>
 * <ul>
 *   <li><b>SALES_LED:</b> Created by sales team, email verified by token click only</li>
 *   <li><b>SELF_SERVICE:</b> Created by user, requires token + verification code</li>
 * </ul>
 */
public enum RegistrationTokenType {

    /**
     * Sales-led onboarding token.
     *
     * <p>Flow:</p>
     * <pre>
     * Sales creates tenant → Email sent with token
     * → User clicks link → Password setup (no code needed)
     * → Auto-login → Onboarding wizard
     * </pre>
     */
    SALES_LED,

    /**
     * Self-service signup token.
     *
     * <p>Flow:</p>
     * <pre>
     * User signs up → Email sent with token
     * → User clicks link → Password setup (email verified by click)
     * → Auto-login → Onboarding wizard
     * </pre>
     * 
     * <p><b>Note:</b> No verification code needed - email link click verifies ownership.
     * Verification codes are only used for unverified contacts during login flows.</p>
     */
    SELF_SERVICE
}

