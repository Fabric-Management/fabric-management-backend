package com.fabricmanagement.common.infrastructure.web.exception;

/**
 * Exception thrown when attempting to operate on two monetary values with different currencies.
 *
 * <p>Lives in {@code common.infrastructure.web.exception} alongside {@link DomainException} to
 * avoid domain → infrastructure layer violation.
 */
public class CurrencyMismatchException extends DomainException {

  public CurrencyMismatchException(String expected, String actual) {
    super(
        "Currency mismatch: expected " + expected + ", got " + actual,
        "CURRENCY_MISMATCH",
        400,
        new Object[] {expected, actual});
  }
}
