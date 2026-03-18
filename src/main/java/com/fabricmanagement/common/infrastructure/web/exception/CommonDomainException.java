package com.fabricmanagement.common.infrastructure.web.exception;

/** Concrete domain exception for common platform rule violations. */
public class CommonDomainException extends DomainException {

  public CommonDomainException(String message) {
    super(message, "DOMAIN_RULE_VIOLATION", 400);
  }

  public CommonDomainException(String message, Throwable cause) {
    super(message, "DOMAIN_RULE_VIOLATION", 400, cause);
  }
}
