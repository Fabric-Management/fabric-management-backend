package com.fabricmanagement.human.common.exception;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;

/** Base exception for all human resources domain rule violations. */
public class HumanDomainException extends DomainException {

  public HumanDomainException(String message) {
    super(message, "HUMAN_RULE_VIOLATION", 400);
  }

  public HumanDomainException(String message, Throwable cause) {
    super(message, "HUMAN_RULE_VIOLATION", 400, cause);
  }
}
