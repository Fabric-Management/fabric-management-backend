package com.fabricmanagement.flowboard.common.exception;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;

/** Base exception for all FlowBoard domain rule violations. */
public class FlowBoardDomainException extends DomainException {

  public FlowBoardDomainException(String message) {
    super(message, "FLOWBOARD_RULE_VIOLATION", 400);
  }

  public FlowBoardDomainException(String message, Throwable cause) {
    super(message, "FLOWBOARD_RULE_VIOLATION", 400, cause);
  }

  protected FlowBoardDomainException(String message, String errorCode, int httpStatus) {
    super(message, errorCode, httpStatus);
  }
}
