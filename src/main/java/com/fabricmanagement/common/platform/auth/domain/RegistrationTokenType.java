package com.fabricmanagement.common.platform.auth.domain;

/**
 * Registration token type - Different flows for different user acquisition channels.
 *
 * <p>Token types:
 *
 * <ul>
 *   <li><b>SALES_LED:</b> Created by sales team, email verified by token click only
 *   <li><b>SELF_SERVICE:</b> Created by user, requires token + verification code
 * </ul>
 */
public enum RegistrationTokenType {

  /**
   * Sales-led onboarding token.
   *
   * <p>Flow:
   *
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
   * <p>Flow:
   *
   * <pre>
   * User signs up → Email sent with token
   * → User clicks link → Password setup (email verified by click)
   * → Auto-login → Onboarding wizard
   * </pre>
   *
   * <p><b>Note:</b> No verification code needed - email link click verifies ownership. Verification
   * codes are only used for unverified contacts during login flows.
   */
  SELF_SERVICE,

  /**
   * Invited user registration token.
   *
   * <p>Flow:
   *
   * <pre>
   * Admin creates user → Invitation email sent with token
   * → User clicks link → Password setup (email verified by click)
   * → Auto-login
   * </pre>
   *
   * <p>Created automatically when a user is added via the internal/external user creation flow.
   */
  INVITED_USER,

  /**
   * Partner portal invited user registration token.
   *
   * <p>Flow:
   *
   * <pre>
   * Tenant admin invites partner user → Invitation email sent with token
   * → User clicks link → /partner-portal/setup?token=...
   * → Password setup → Partner portal access
   * </pre>
   *
   * <p>Uses a dedicated email template and partner-portal-specific setup URL.
   */
  PARTNER_INVITED_USER
}
