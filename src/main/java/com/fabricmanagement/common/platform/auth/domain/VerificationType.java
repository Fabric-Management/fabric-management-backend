package com.fabricmanagement.common.platform.auth.domain;

/** Verification code type classification. */
public enum VerificationType {

  /**
   * Registration verification
   *
   * <p>Sent during user registration flow
   */
  REGISTRATION,

  /**
   * Password reset verification
   *
   * <p>Sent when user requests password reset
   */
  PASSWORD_RESET,

  /**
   * Email verification
   *
   * <p>Sent to verify email ownership
   */
  EMAIL_VERIFICATION,

  /**
   * Phone verification
   *
   * <p>Sent to verify phone ownership
   */
  PHONE_VERIFICATION
}
