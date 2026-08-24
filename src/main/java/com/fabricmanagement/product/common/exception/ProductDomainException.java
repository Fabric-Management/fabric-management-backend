package com.fabricmanagement.product.common.exception;

import com.fabricmanagement.common.infrastructure.web.exception.DomainException;

/** Base exception for product definition and composition rule violations. */
public class ProductDomainException extends DomainException {

  public ProductDomainException(String message) {
    super(message, "PRODUCT_RULE_VIOLATION", 400);
  }

  public ProductDomainException(String message, Throwable cause) {
    super(message, "PRODUCT_RULE_VIOLATION", 400, cause);
  }

  protected ProductDomainException(String message, String errorCode, int httpStatus) {
    super(message, errorCode, httpStatus);
  }

  protected ProductDomainException(
      String message, String errorCode, int httpStatus, Throwable cause) {
    super(message, errorCode, httpStatus, cause);
  }

  protected ProductDomainException(
      String message, String errorCode, int httpStatus, Object[] args) {
    super(message, errorCode, httpStatus, args);
  }
}
