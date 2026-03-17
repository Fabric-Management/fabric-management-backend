package com.fabricmanagement.production.common.exception;

import java.math.BigDecimal;

/**
 * Exception thrown when a stock operation cannot be fulfilled due to insufficient available
 * quantity.
 *
 * <p>Used in batch reservation, release, and consume operations. The exception carries both the
 * requested and available quantities so the {@code GlobalExceptionHandler} can return a rich error
 * response that the frontend can display directly to the operator.
 *
 * <h2>HTTP Response — 422 Unprocessable Entity</h2>
 *
 * <pre>
 * {
 *   "code": "INSUFFICIENT_STOCK",
 *   "message": "Insufficient stock in batch abc-123: requested 50.00 kg, available 30.00 kg.",
 *   "details": {
 *     "batchId": "abc-123",
 *     "requested": 50.00,
 *     "available": 30.00,
 *     "unit": "kg"
 *   }
 * }
 * </pre>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * if (batch.getAvailableQuantity().compareTo(requested) < 0) {
 *   throw new InsufficientStockException(
 *       batch.getId().toString(),
 *       requested,
 *       batch.getAvailableQuantity(),
 *       batch.getUnit().name());
 * }
 * }</pre>
 */
public class InsufficientStockException extends ProductionDomainException {

  private final String batchId;
  private final BigDecimal requested;
  private final BigDecimal available;
  private final String unit;

  public InsufficientStockException(
      String batchId, BigDecimal requested, BigDecimal available, String unit) {
    super(buildMessage(batchId, requested, available, unit), "INSUFFICIENT_STOCK", 422);
    this.batchId = batchId;
    this.requested = requested;
    this.available = available;
    this.unit = unit;
  }

  private static String buildMessage(
      String batchId, BigDecimal requested, BigDecimal available, String unit) {
    return String.format(
        "Insufficient stock in batch %s: requested %.2f %s, available %.2f %s.",
        batchId, requested, unit, available, unit);
  }

  public String getBatchId() {
    return batchId;
  }

  public BigDecimal getRequested() {
    return requested;
  }

  public BigDecimal getAvailable() {
    return available;
  }

  public String getUnit() {
    return unit;
  }
}
