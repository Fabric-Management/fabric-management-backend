package com.fabricmanagement.production.execution.batch.domain.exception;

import java.util.UUID;

/** Raised when a lot intent is expressed in a non-canonical unit for its batch. */
public class LotIntentUnitMismatchException extends BatchDomainException {

  public LotIntentUnitMismatchException(UUID batchId, String sentUnit, String expectedUnit) {
    super(
        "Lot intent unit mismatch for batch %s: sent %s, expected %s"
            .formatted(batchId, sentUnit, expectedUnit),
        "LOT_INTENT_UNIT_MISMATCH",
        422,
        new Object[] {batchId, sentUnit, expectedUnit});
  }
}
