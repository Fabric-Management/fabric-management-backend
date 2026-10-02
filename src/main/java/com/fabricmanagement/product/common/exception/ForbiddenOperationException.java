package com.fabricmanagement.product.common.exception;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;

/** Product operation rejected for the current tenant or execution context. */
public class ForbiddenOperationException extends DomainException {

  public ForbiddenOperationException(String message) {
    super(message, "FORBIDDEN_OPERATION", 403);
  }

  /** Forbidden operation with a stable, client-translatable code. */
  public ForbiddenOperationException(String message, String errorCode) {
    super(message, errorCode, 403);
  }

  public ForbiddenOperationException(String message, Throwable cause) {
    super(message, "FORBIDDEN_OPERATION", 403, cause);
  }
}
