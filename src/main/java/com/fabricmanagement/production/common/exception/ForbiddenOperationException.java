package com.fabricmanagement.production.common.exception;

/**
 * Exception thrown when an operation is not allowed for the current context (e.g. tenant attempting
 * a platform-only operation).
 *
 * <p>Maps to HTTP 403 Forbidden.
 */
public class ForbiddenOperationException extends RuntimeException {

  public ForbiddenOperationException(String message) {
    super(message);
  }

  public ForbiddenOperationException(String message, Throwable cause) {
    super(message, cause);
  }
}
