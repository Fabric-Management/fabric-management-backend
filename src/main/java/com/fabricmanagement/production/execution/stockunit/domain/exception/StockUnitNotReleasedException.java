package com.fabricmanagement.production.execution.stockunit.domain.exception;

import com.fabricmanagement.production.execution.stockunit.domain.QualityDisposition;

/** Raised when an operation requiring accepted stock targets an unreleased physical unit. */
public class StockUnitNotReleasedException extends StockUnitDomainException {

  public StockUnitNotReleasedException(String barcode, QualityDisposition disposition) {
    super(
        "StockUnit "
            + barcode
            + " is not released for operational use (disposition="
            + disposition
            + ")",
        "STOCK_UNIT_NOT_RELEASED",
        422);
  }
}
