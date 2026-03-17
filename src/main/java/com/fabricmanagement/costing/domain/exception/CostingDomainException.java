package com.fabricmanagement.costing.domain.exception;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;

/**
 * Base exception for the Costing module.
 *
 * <p>All costing-specific exceptions should extend this class to enable consistent error handling
 * at the API boundary and module-level exception classification.
 */
public class CostingDomainException extends DomainException {

  private static final String ERROR_CODE = "COSTING_DOMAIN_ERROR";
  private static final int HTTP_STATUS = 422;

  public CostingDomainException(String message) {
    super(message, ERROR_CODE, HTTP_STATUS);
  }

  public CostingDomainException(String message, Throwable cause) {
    super(message, ERROR_CODE, HTTP_STATUS, cause);
  }
}
