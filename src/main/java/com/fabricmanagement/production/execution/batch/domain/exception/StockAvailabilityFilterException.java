package com.fabricmanagement.production.execution.batch.domain.exception;

/** Raised when stock-availability filters would be ambiguous or unbounded. */
public class StockAvailabilityFilterException extends BatchDomainException {

  public StockAvailabilityFilterException(String message) {
    super(message, "STOCK_AVAILABILITY_FILTER_INVALID", 422);
  }
}
