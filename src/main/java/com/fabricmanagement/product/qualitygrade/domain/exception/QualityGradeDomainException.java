package com.fabricmanagement.product.qualitygrade.domain.exception;

import com.fabricmanagement.product.common.exception.ProductDomainException;

/** Base exception for quality-grade definition rule violations. */
public class QualityGradeDomainException extends ProductDomainException {

  public QualityGradeDomainException(String message) {
    super(message);
  }

  public QualityGradeDomainException(String message, Throwable cause) {
    super(message, cause);
  }
}
