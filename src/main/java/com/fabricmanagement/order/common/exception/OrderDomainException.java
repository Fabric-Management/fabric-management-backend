package com.fabricmanagement.order.common.exception;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;

/** Base exception for all order domain rule violations. */
public class OrderDomainException extends DomainException {

  public OrderDomainException(String message) {
    super(message, "ORDER_RULE_VIOLATION", 400);
  }

  public OrderDomainException(String message, Throwable cause) {
    super(message, "ORDER_RULE_VIOLATION", 400, cause);
  }
}
