package com.fabricmanagement.common.platform.auth.app;

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

  private AuthValidationResult(boolean valid, String reason, Instant lockedUntil) {
    this.valid = valid;
    this.reason = reason != null ? reason : "";
    this.lockedUntil = lockedUntil;
  }

  public static AuthValidationResult valid() {
    return new AuthValidationResult(true, null, null);
  }

  public static AuthValidationResult notVerified() {
    return new AuthValidationResult(
        false, "Account not verified. Please complete registration.", null);
  }

  public static AuthValidationResult locked(Instant lockedUntil) {
    return new AuthValidationResult(
        false, "Account is temporarily locked. Try again later.", lockedUntil);
  }

  public static AuthValidationResult inactive() {
    return new AuthValidationResult(false, "Account is deactivated", null);
  }
}
