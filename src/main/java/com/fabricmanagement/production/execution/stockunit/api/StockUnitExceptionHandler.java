package com.fabricmanagement.production.execution.stockunit.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.production.execution.stockunit.domain.exception.InsufficientWeightException;
import com.fabricmanagement.production.execution.stockunit.domain.exception.InvalidPackageTypeException;
import com.fabricmanagement.production.execution.stockunit.domain.exception.StockUnitDomainException;
import com.fabricmanagement.production.execution.stockunit.domain.exception.WeightReconciliationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Module-scoped exception handler for StockUnit domain exceptions. */
@Slf4j
@RestControllerAdvice(assignableTypes = StockUnitController.class)
public class StockUnitExceptionHandler {

  @ExceptionHandler(InsufficientWeightException.class)
  public ResponseEntity<ApiResponse<Void>> handleInsufficientWeight(
      InsufficientWeightException ex) {
    log.warn("Insufficient weight: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(ApiResponse.error("INSUFFICIENT_WEIGHT", ex.getMessage()));
  }

  @ExceptionHandler(InvalidPackageTypeException.class)
  public ResponseEntity<ApiResponse<Void>> handleInvalidPackageType(
      InvalidPackageTypeException ex) {
    log.warn("Invalid package type: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error("INVALID_PACKAGE_TYPE", ex.getMessage()));
  }

  @ExceptionHandler(WeightReconciliationException.class)
  public ResponseEntity<ApiResponse<Void>> handleWeightReconciliation(
      WeightReconciliationException ex) {
    log.warn("Weight reconciliation conflict: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiResponse.error("WEIGHT_RECONCILIATION_CONFLICT", ex.getMessage()));
  }

  @ExceptionHandler(StockUnitDomainException.class)
  public ResponseEntity<ApiResponse<Void>> handleDomainException(StockUnitDomainException ex) {
    log.warn("StockUnit domain error: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error("STOCK_UNIT_ERROR", ex.getMessage()));
  }
}
