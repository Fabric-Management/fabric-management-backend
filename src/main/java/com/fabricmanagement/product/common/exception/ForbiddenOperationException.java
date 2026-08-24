package com.fabricmanagement.product.common.exception;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;

/** Product operation rejected for the current tenant or execution context. */
public class ForbiddenOperationException extends DomainException {

  public ForbiddenOperationException(String message) {
    super(message, "FORBIDDEN_OPERATION", 403);
  }

  public ForbiddenOperationException(String message, Throwable cause) {
    super(message, "FORBIDDEN_OPERATION", 403, cause);
  }
}
