package com.fabricmanagement.logistics.common.exception;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;

/** Base exception for all logistics domain rule violations. */
public class LogisticsDomainException extends DomainException {

  public static LogisticsDomainException rule(String code, String message) {
    return new LogisticsDomainException(message, code);
  }

  private LogisticsDomainException(String message, String code) {
    super(message, code, 422);
  }

  public LogisticsDomainException(String message) {
    super(message, "LOGISTICS_RULE_VIOLATION", 400);
  }

  public LogisticsDomainException(String message, Throwable cause) {
    super(message, "LOGISTICS_RULE_VIOLATION", 400, cause);
  }
}
