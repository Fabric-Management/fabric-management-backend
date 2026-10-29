package com.fabricmanagement.production.core.stockunit.domain.exception;

public final class StockUnitCutPlanException extends StockUnitDomainException {
  private StockUnitCutPlanException(String code, String message, int status) {
    super(message, "CUT_PLAN_" + code, status);
  }

  public static StockUnitCutPlanException conflict(String code, String message) {
    return new StockUnitCutPlanException(code, message, 409);
  }

  public static StockUnitCutPlanException invalid(String code, String message) {
    return new StockUnitCutPlanException(code, message, 400);
  }
}
