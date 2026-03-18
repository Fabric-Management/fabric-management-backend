package com.fabricmanagement.production.common.exception;

import java.math.BigDecimal;
import java.util.UUID;

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
 *   "message": "Insufficient stock in batch FB-001 (id: ...): requested 50.00 kg, available 30.00 kg.",
 *   "details": {
 *     "batchId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
 *     "batchCode": "FB-001",
 *     "requested": 50.00,
 *     "available": 30.00,
 *     "unit": "KG"
 *   }
 * }
 * </pre>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * if (batch.getAvailableQuantity().compareTo(requested) < 0) {
 *   throw new InsufficientStockException(
 *       batch.getId(),
 *       batch.getBatchCode(),
 *       requested,
 *       batch.getAvailableQuantity(),
 *       batch.getUnit());
 * }
 * }</pre>
 */
public class InsufficientStockException extends ProductionDomainException {

  /** UUID of the batch — for frontend deep-link navigation. */
  private final UUID batchId;

  /** Human-readable batch code — displayed in error messages. */
  private final String batchCode;

  private final BigDecimal requested;
  private final BigDecimal available;
  private final String unit;

  public InsufficientStockException(
      UUID batchId, String batchCode, BigDecimal requested, BigDecimal available, String unit) {
    super(buildMessage(batchCode, requested, available, unit), "INSUFFICIENT_STOCK", 422);
    this.batchId = batchId;
    this.batchCode = batchCode;
    this.requested = requested;
    this.available = available;
    this.unit = unit;
  }

  private static String buildMessage(
      String batchCode, BigDecimal requested, BigDecimal available, String unit) {
    return String.format(
        "Insufficient stock in batch %s: requested %.2f %s, available %.2f %s.",
        batchCode, requested, unit, available, unit);
  }

  /** UUID of the batch for frontend deep-link. */
  public UUID getBatchId() {
    return batchId;
  }

  /** Human-readable batch code for display. */
  public String getBatchCode() {
    return batchCode;
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
