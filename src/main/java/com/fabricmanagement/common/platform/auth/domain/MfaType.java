package com.fabricmanagement.common.platform.auth.domain;

/**
 * Multi-Factor Authentication (MFA) methods available to users.
 *
 * <ul>
 *   <li>{@link #NONE} - MFA is disabled
 *   <li>{@link #TOTP} - Google/Microsoft Authenticator (Recommended)
 *   <li>{@link #EMAIL} - OTP via registered Email
 *   <li>{@link #SMS} - OTP via SMS
 *   <li>{@link #WHATSAPP} - OTP via WhatsApp
 * </ul>
 */
public enum MfaType {
  NONE,
  TOTP,
  EMAIL,
  SMS,
  WHATSAPP;

  public boolean isEnabled() {
    return this != NONE;
  }
}
