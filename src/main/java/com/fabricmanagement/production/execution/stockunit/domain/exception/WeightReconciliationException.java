package com.fabricmanagement.production.execution.stockunit.domain.exception;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Thrown when the Batch quantity does not match the sum of its StockUnit current weights.
 *
 * <p>This indicates a reconciliation inconsistency:
 *
 * <pre>
 * Batch.quantity ≠ SUM(StockUnit.currentWeight) WHERE batchId = batch.id
 *                                                 AND status NOT IN (DISPOSED)
 * </pre>
 *
 * <p>This exception is raised by the {@code StockUnitReconciliationService} during its daily job.
 * It should NOT block normal operations in passive mode — it is used to generate admin alerts.
 *
 * <p>HTTP 409 — Conflict.
 */
public class WeightReconciliationException extends StockUnitDomainException {

  public WeightReconciliationException(
      UUID batchId, String batchCode, BigDecimal batchQuantity, BigDecimal sumUnitWeights) {
    super(
        String.format(
            "Reconciliation mismatch for Batch [%s / %s]: batch.quantity=%.3f but"
                + " SUM(stockUnit.currentWeight)=%.3f (delta=%.3f).",
            batchId,
            batchCode,
            batchQuantity,
            sumUnitWeights,
            batchQuantity.subtract(sumUnitWeights)),
        "WEIGHT_RECONCILIATION_MISMATCH",
        409);
  }
}
