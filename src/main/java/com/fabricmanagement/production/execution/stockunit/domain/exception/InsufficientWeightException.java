package com.fabricmanagement.production.execution.stockunit.domain.exception;

import java.math.BigDecimal;

/**
 * Thrown when a consumption operation would exceed the available weight on a StockUnit.
 *
 * <p>HTTP 422 — Unprocessable Entity.
 */
public class InsufficientWeightException extends StockUnitDomainException {

  public InsufficientWeightException(String barcode, BigDecimal requested, BigDecimal available) {
    super(
        String.format(
            "StockUnit [%s] has insufficient weight: requested=%.3f, available=%.3f",
            barcode, requested, available),
        "INSUFFICIENT_WEIGHT",
        422);
  }
}
