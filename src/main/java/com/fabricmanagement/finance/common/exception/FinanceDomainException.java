package com.fabricmanagement.finance.common.exception;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;

/** Base exception for all finance domain rule violations. */
public class FinanceDomainException extends DomainException {

  public FinanceDomainException(String message) {
    super(message, "FINANCE_RULE_VIOLATION", 400);
  }

  public FinanceDomainException(String message, Throwable cause) {
    super(message, "FINANCE_RULE_VIOLATION", 400, cause);
  }
}
