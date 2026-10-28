package com.fabricmanagement.common.infrastructure.logging;

/** Keeps failure type and source frames without messages, causes or suppressed payloads. */
public final class LogSafeFailure {
  private LogSafeFailure() {}

  public static Throwable snapshot(Throwable failure) {
    // Provider/SQL exceptions can embed the complete rendered email, tokens or passwords.
    // Do not attach the original as a cause or suppressed exception.
    var safe = new RuntimeException("failureType=" + failure.getClass().getName());
    safe.setStackTrace(failure.getStackTrace());
    return safe;
  }
}
