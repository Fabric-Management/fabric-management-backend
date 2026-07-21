package com.fabricmanagement.production.execution.batch.domain.exception;

import com.fabricmanagement.production.execution.batch.domain.BatchStatus;

/** Raised when a real operational batch state blocks released-unit consumption. */
public class BatchNotConsumableException extends BatchDomainException {

  public BatchNotConsumableException(String batchCode, BatchStatus status) {
    super(
        "Batch " + batchCode + " is not consumable in operational status " + status,
        "BATCH_NOT_CONSUMABLE",
        422);
  }
}
