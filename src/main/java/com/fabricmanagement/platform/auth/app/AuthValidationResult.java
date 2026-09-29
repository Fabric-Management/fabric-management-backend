package com.fabricmanagement.platform.auth.app;

import java.time.Instant;
import lombok.Getter;

/**
 * Result of validating an AuthUser (verified, active, not locked).
 *
 * <p>Used by {@link AuthUserResolutionService} and consumed by LoginService, PasswordResetService,
 * etc.
 */
@Getter
public class AuthValidationResult {

  private final boolean valid;
  private final String reason;
  private final Instant lockedUntil;

  /** Machine-readable code the API returns when this result blocks a login; empty when valid. */
  private final String errorCode;

  /** HTTP status the API returns when this result blocks a login; 0 when valid. */
  private final int httpStatus;

  private AuthValidationResult(
      boolean valid, String reason, Instant lockedUntil, String errorCode, int httpStatus) {
    this.valid = valid;
    this.reason = reason != null ? reason : "";
    this.lockedUntil = lockedUntil;
    this.errorCode = errorCode != null ? errorCode : "";
    this.httpStatus = httpStatus;
  }

  public static AuthValidationResult valid() {
    return new AuthValidationResult(true, null, null, null, 0);
  }

  public static AuthValidationResult notVerified() {
    return new AuthValidationResult(
        false,
        "Account not verified. Please complete registration.",
        null,
        "AUTH_ACCOUNT_NOT_VERIFIED",
        400);
  }

  public static AuthValidationResult locked(Instant lockedUntil) {
    return new AuthValidationResult(
        false,
        "Account is temporarily locked. Try again later.",
        lockedUntil,
        "AUTH_ACCOUNT_LOCKED",
        400);
  }

  public static AuthValidationResult inactive() {
    return new AuthValidationResult(
        false, "Account is deactivated", null, "AUTH_ACCOUNT_DEACTIVATED", 400);
  }

  public static AuthValidationResult passwordResetRequired() {
    return new AuthValidationResult(
        false, "Password reset required", null, "AUTH_PASSWORD_RESET_REQUIRED", 409);
  }
}
