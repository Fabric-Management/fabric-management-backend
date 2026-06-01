package com.fabricmanagement.sales.common.exception;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;

/** Base exception for all order domain rule violations. */
public class OrderDomainException extends DomainException {

  public OrderDomainException(String message) {
    super(message, "ORDER_RULE_VIOLATION", 400);
  }

  /**
   * State-conflict variant — allows 409 for edit guard violations. Only to be used for resource
   * state conflicts, not for validation/domain rule failures.
   */
  public OrderDomainException(String message, int httpStatus) {
    super(message, "ORDER_RULE_VIOLATION", httpStatus);
  }

  public OrderDomainException(String message, Throwable cause) {
    super(message, "ORDER_RULE_VIOLATION", 400, cause);
  }
}
