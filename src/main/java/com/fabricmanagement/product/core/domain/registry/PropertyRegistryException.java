package com.fabricmanagement.product.core.domain.registry;

import com.fabricmanagement.product.common.exception.ProductDomainException;

public class PropertyRegistryException extends ProductDomainException {

  public PropertyRegistryException(String message) {
    super(message);
  }

  public PropertyRegistryException(String message, Throwable cause) {
    super(message, cause);
  }
}
