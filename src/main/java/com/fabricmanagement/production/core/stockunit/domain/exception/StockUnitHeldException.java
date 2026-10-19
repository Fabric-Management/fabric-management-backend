package com.fabricmanagement.production.core.stockunit.domain.exception;

/** A draft, quote, order or hard allocation already owns one of the requested pieces. */
public class StockUnitHeldException extends StockUnitDomainException {
  public StockUnitHeldException() {
    super(
        "Selected stock is held by another owner; refresh the proposal",
        "STOCK_HELD_BY_ANOTHER_OWNER",
        409);
  }
}
