package com.fabricmanagement.product.yarn.domain.exception;

import com.fabricmanagement.product.common.exception.ProductDomainException;

/** Base exception for yarn master-data and catalogue rule violations. */
public class YarnDomainException extends ProductDomainException {

  public YarnDomainException(String message) {
    super(message);
  }

  public YarnDomainException(String message, Throwable cause) {
    super(message, cause);
  }
}
