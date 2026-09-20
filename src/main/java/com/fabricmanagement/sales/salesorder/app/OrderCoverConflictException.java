package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;

public class OrderCoverConflictException extends DomainException {
  public OrderCoverConflictException(String message, String code) {
    super(message, code, 409);
  }
}
