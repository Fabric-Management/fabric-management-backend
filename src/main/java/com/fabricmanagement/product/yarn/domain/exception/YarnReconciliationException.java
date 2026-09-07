package com.fabricmanagement.product.yarn.domain.exception;

import com.fabricmanagement.product.common.exception.ProductDomainException;

public class YarnReconciliationException extends ProductDomainException {

  public YarnReconciliationException(String message, String code) {
    super(message, code, 409);
  }
}
