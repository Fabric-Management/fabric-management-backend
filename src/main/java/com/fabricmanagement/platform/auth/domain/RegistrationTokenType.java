package com.fabricmanagement.platform.auth.domain;

/**
 * Registration token type - Different flows for different user acquisition channels.
 *
 * <p>Token types:
 *
 * <ul>
 *   <li><b>SALES_LED:</b> Created by sales team, email verified by token click only
 *   <li><b>SELF_SERVICE:</b> legacy, no longer issued (see the constant)
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
   * Legacy self-service signup token. <b>No longer issued</b> (SIGNUP-DEFER-1): a self-service
   * signup is a {@code pending_signup} with a hashed link, and the company is built when the link
   * is used. Kept because the column's check constraint and existing rows name it; a remaining
   * token of this type is used like a sales-led one.
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
