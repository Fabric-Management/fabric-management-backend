package com.fabricmanagement.production.common.exception;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;

/**
 * Exception thrown when an operation is not allowed for the current context (e.g. tenant attempting
 * a platform-only operation).
 *
 * <p>Maps to HTTP 403 Forbidden.
 */
public class ForbiddenOperationException extends DomainException {

  public ForbiddenOperationException(String message) {
    super(message, "FORBIDDEN_OPERATION", 403);
  }

  public ForbiddenOperationException(String message, Throwable cause) {
    super(message, "FORBIDDEN_OPERATION", 403, cause);
  }
}
