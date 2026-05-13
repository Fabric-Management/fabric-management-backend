package com.fabricmanagement.production.execution.stockunit.domain.exception;

import com.fabricmanagement.production.common.exception.ProductionDomainException;

/**
 * Base exception for StockUnit domain rule violations.
 *
 * <p>Use this (or a more specific subclass) for any business rule violation during StockUnit
 * lifecycle management. Prefer the more specific exceptions where applicable:
 *
 * <ul>
 *   <li>{@link InsufficientWeightException} — consumption exceeds available weight
 *   <li>{@link InvalidPackageTypeException} — incompatible package/product type combination
 *   <li>{@link WeightReconciliationException} — Batch ↔ StockUnit weight sum mismatch
 * </ul>
 */
public class StockUnitDomainException extends ProductionDomainException {

  public StockUnitDomainException(String message) {
    super(message, "STOCK_UNIT_RULE_VIOLATION", 400);
  }

  public StockUnitDomainException(String message, Throwable cause) {
    super(message, cause);
  }

  protected StockUnitDomainException(String message, String errorCode, int httpStatus) {
    super(message, errorCode, httpStatus);
  }
}
