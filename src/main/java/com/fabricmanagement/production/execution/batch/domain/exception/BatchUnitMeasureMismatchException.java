package com.fabricmanagement.production.execution.batch.domain.exception;

import java.util.UUID;

/** Raised when a batch bookkeeping unit cannot represent its canonical primary measure. */
public class BatchUnitMeasureMismatchException extends BatchDomainException {

  public BatchUnitMeasureMismatchException(UUID batchId, String batchUnit, String expectedUnit) {
    super(
        "Batch unit/measure mismatch for batch %s: batch unit %s, expected %s-compatible unit"
            .formatted(batchId, batchUnit, expectedUnit),
        "BATCH_UNIT_MEASURE_MISMATCH",
        409,
        new Object[] {batchId, batchUnit, expectedUnit});
  }
}
