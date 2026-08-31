package com.fabricmanagement.product.yarn.domain.exception;

import com.fabricmanagement.product.common.exception.ProductDomainException;
import java.util.List;

/** Base exception for yarn master-data and catalogue rule violations. */
public class YarnDomainException extends ProductDomainException {

  private final List<String> invariantIds;

  public YarnDomainException(String message) {
    super(message);
    this.invariantIds = List.of();
  }

  public YarnDomainException(String message, Throwable cause) {
    super(message, cause);
    this.invariantIds = List.of();
  }

  public YarnDomainException(String invariantId, String message) {
    super(message, "YARN_INVARIANT_VIOLATION", 409);
    this.invariantIds = List.of(invariantId);
    withDetail("invariantIds", this.invariantIds);
  }

  public YarnDomainException(List<String> invariantIds, String message) {
    super(message, "YARN_INVARIANT_VIOLATION", 409);
    this.invariantIds = List.copyOf(invariantIds);
    withDetail("invariantIds", this.invariantIds);
  }

  public List<String> getInvariantIds() {
    return invariantIds;
  }
}
