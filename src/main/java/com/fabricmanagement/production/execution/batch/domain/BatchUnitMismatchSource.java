package com.fabricmanagement.production.execution.batch.domain;

/** Origin of a quantity excluded from canonical stock arithmetic. */
@io.swagger.v3.oas.annotations.media.Schema(name = "ProductionStockAvailabilityUnitMismatchSource")
public enum BatchUnitMismatchSource {
  SOFT_INTENT,
  HARD_RESERVATION,
  PIECE_WEIGHT,
  PIECE_LENGTH,
  BATCH_QUANTITY
}
